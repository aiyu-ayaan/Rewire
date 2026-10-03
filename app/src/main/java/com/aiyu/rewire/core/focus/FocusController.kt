package com.aiyu.rewire.core.focus

import com.aiyu.rewire.core.notifications.FocusAlert
import com.aiyu.rewire.core.notifications.FocusDndManager
import com.aiyu.rewire.core.notifications.RewireNotifier
import com.aiyu.rewire.core.settings.Settings
import com.aiyu.rewire.data.EventRepository
import com.aiyu.rewire.data.FocusSessionRepository
import com.aiyu.rewire.domain.analytics.HabitEvent
import com.aiyu.rewire.domain.analytics.HabitEventType
import com.aiyu.rewire.domain.focus.FocusConfig
import com.aiyu.rewire.domain.focus.FocusSession
import com.aiyu.rewire.domain.focus.FocusSessionStatus
import com.aiyu.rewire.domain.focus.FocusState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * The one focus timer, app-scoped so it outlives the UI. Every transition is saved to Room, so a
 * killed process resumes the same session; [FocusTimerService] keeps the process alive meanwhile.
 * All calls on the main thread.
 */
class FocusController(
    private val state: MutableStateFlow<FocusState>,
    private val sessions: FocusSessionRepository,
    private val events: EventRepository,
    private val notifier: RewireNotifier,
    private val dnd: FocusDndManager,
    private val settings: StateFlow<Settings?>,
    private val scope: CoroutineScope,
    /** Starts / stops the foreground service; the platform side lives in the service. */
    private val keepAlive: (active: Boolean) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val current: StateFlow<FocusState> = state.asStateFlow()

    /** Wall clock for UI; ticks only while running. */
    private val _now = MutableStateFlow(clock())
    val now: StateFlow<Long> = _now.asStateFlow()

    /** Session that just ended and may get an achievement note; null once saved or skipped. */
    private val _awaitingNote = MutableStateFlow<FocusSession?>(null)
    val awaitingNote: StateFlow<FocusSession?> = _awaitingNote.asStateFlow()

    private var sessionId: String? = null
    private var ticker: Job? = null

    /** App start: pick up a session the previous process left running, else clean up its leftovers. */
    fun restore() {
        val saved = sessions.active()
        if (saved == null) {
            dnd.restoreDnd()
            return
        }
        sessionId = saved.id
        state.value = saved.state
        // Phases that ended while the process was dead are logged and chimed now (late, never lost).
        transition(saved.state.advance(clock()), force = true)
    }

    fun start(config: FocusConfig) {
        if (!config.isValid || state.value.isActive) return
        sessionId = UUID.randomUUID().toString()
        _awaitingNote.value = null
        transition(FocusState().start(config, clock()))
        events.log(HabitEventType.FOCUS_STARTED, metadata = mapOf("focus" to "${config.focusMinutes}", "break" to "${config.breakMinutes}", "cycles" to "${config.cycles}"))
    }

    fun pause() { if (state.value.isRunning) { transition(state.value.pause(clock())); events.log(HabitEventType.FOCUS_PAUSED) } }
    fun resume() { if (state.value.status == FocusSessionStatus.PAUSED) { transition(state.value.resume(clock())); events.log(HabitEventType.FOCUS_RESUMED) } }
    fun skipBreak() = transition(state.value.skipBreak(clock()))

    fun end() {
        val before = state.value
        if (!before.isActive) return
        val now = clock()
        val partial = if (before.phase == FocusSessionStatus.FOCUSING) elapsedMinutes(before, now) else 0
        transition(before.cancel(now), focusedMillis = before.focusedMillis(now))
        events.log(HabitEventType.FOCUS_CANCELLED, metadata = mapOf(HabitEvent.KEY_FOCUS_MINUTES to "$partial"))
    }

    /** Back to setup after the result screen. */
    fun reset(draft: FocusConfig) {
        if (state.value.isActive) return
        sessionId = null
        transition(FocusState(config = draft))
    }

    fun saveNote(note: String?) {
        val s = _awaitingNote.value ?: return
        sessions.setNote(s.id, note)
        _awaitingNote.value = null
    }

    fun skipNote() { _awaitingNote.value = null }

    fun onDndSettingChanged(enabled: Boolean) {
        if (!enabled) dnd.restoreDnd()
        else if (state.value.isRunning && state.value.phase == FocusSessionStatus.FOCUSING) applyDnd()
    }

    private fun transition(next: FocusState, focusedMillis: Long? = null, force: Boolean = false) {
        val prev = state.value
        if (next == prev && !force) return
        val now = clock()
        state.value = next
        _now.value = now
        persist(next, focusedMillis ?: next.focusedMillis(now))
        updateDnd(next)
        onPhaseChange(prev, next)
        if (prev.isActive != next.isActive || force) keepAlive(next.isActive)
        if (next.isRunning) ensureTicker() else { ticker?.cancel(); ticker = null }
    }

    private fun persist(s: FocusState, focusedMillis: Long) {
        val id = sessionId ?: return
        if (s.startedAt == null) return
        val session = FocusSession(id, s, focusedMillis)
        sessions.save(session)
        if (session.isFinished) {
            _awaitingNote.value = session
            sessionId = null
        }
    }

    private fun updateDnd(next: FocusState) {
        val shouldDnd = (settings.value?.focusDndEnabled ?: true) && next.isRunning && next.phase == FocusSessionStatus.FOCUSING
        if (shouldDnd) applyDnd() else dnd.restoreDnd()
    }

    private fun applyDnd() {
        notifier.letFocusThroughDnd()
        dnd.applyFocusDnd()
    }

    private fun ensureTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                // Nobody watching the digits: sleep straight to the phase boundary instead of ticking.
                // The idle sleep ends early when a collector returns (rotation, back to the app), so the digits never lag.
                if (_now.subscriptionCount.value > 0) delay(TICK_MILLIS)
                else withTimeoutOrNull(state.value.remaining(clock()).coerceIn(TICK_MILLIS, IDLE_TICK_MILLIS)) {
                    _now.subscriptionCount.first { it > 0 }
                }
                val now = clock()
                _now.value = now
                val next = state.value.advance(now)
                if (next != state.value) transition(next)
            }
        }
    }

    /** Logs + notifies on natural phase boundaries (not on user pause/resume). */
    private fun onPhaseChange(prev: FocusState, next: FocusState) {
        if (prev.phase == next.phase && prev.cycle == next.cycle && next.status != FocusSessionStatus.COMPLETED) return
        val focusMin = "${prev.config.focusMillis / FocusState.MINUTE}"
        when {
            next.status == FocusSessionStatus.COMPLETED && prev.status != FocusSessionStatus.COMPLETED -> {
                events.log(HabitEventType.FOCUS_COMPLETED, metadata = mapOf(HabitEvent.KEY_FOCUS_MINUTES to focusMin))
                notifier.focusAlert(FocusAlert.COMPLETED, next)
                notifier.playBreakTone()
            }
            prev.phase == FocusSessionStatus.FOCUSING && next.status == FocusSessionStatus.BREAK -> {
                events.log(HabitEventType.BREAK_STARTED, metadata = mapOf(HabitEvent.KEY_FOCUS_MINUTES to focusMin))
                notifier.focusAlert(FocusAlert.BREAK_STARTED, next)
                notifier.playBreakTone()
            }
            prev.phase == FocusSessionStatus.BREAK && next.phase == FocusSessionStatus.FOCUSING -> {
                val spent = elapsedMinutes(prev, clock()).coerceAtMost((prev.config.breakMillis / FocusState.MINUTE).toInt())
                events.log(HabitEventType.BREAK_COMPLETED, metadata = mapOf(HabitEvent.KEY_BREAK_MINUTES to "$spent"))
                notifier.focusAlert(FocusAlert.FOCUS_RESUMED, next)
                notifier.playFocusTone()
            }
            prev.phase == FocusSessionStatus.FOCUSING && next.phase == FocusSessionStatus.FOCUSING && next.cycle > prev.cycle -> {
                events.log(HabitEventType.CYCLE_COMPLETED, metadata = mapOf(HabitEvent.KEY_FOCUS_MINUTES to focusMin))
            }
        }
    }

    private fun elapsedMinutes(s: FocusState, now: Long) = ((s.phaseMillis() - s.remaining(now)) / FocusState.MINUTE).toInt()

    private companion object {
        const val TICK_MILLIS = 250L
        const val IDLE_TICK_MILLIS = 30_000L
    }
}
