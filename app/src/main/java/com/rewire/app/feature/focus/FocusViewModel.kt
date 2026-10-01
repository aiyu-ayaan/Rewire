package com.rewire.app.feature.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rewire.app.AppContainer
import com.rewire.app.core.datastore.FocusBypass
import com.rewire.app.core.notifications.FocusAlert
import com.rewire.app.domain.analytics.HabitEvent
import com.rewire.app.domain.analytics.HabitEventType
import com.rewire.app.domain.focus.FocusConfig
import com.rewire.app.domain.focus.FocusSessionStatus
import com.rewire.app.domain.focus.FocusState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// ponytail: timer lives in ViewModel (survives rotation, not process death); Phase 3 moves it to a ForegroundService.
class FocusViewModel(
    private val c: AppContainer,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    // Shared with the Guard engine so focus bypass rules see the live session.
    private val _state = c.focusState
    val state: StateFlow<FocusState> = _state.asStateFlow()

    /** Wall clock for UI; ticks only while running. */
    private val _now = MutableStateFlow(clock())
    val now: StateFlow<Long> = _now.asStateFlow()

    private val _draft = MutableStateFlow(FocusConfig(focusMinutes = 25, breakMinutes = 5, cycles = 4))
    val draft: StateFlow<FocusConfig> = _draft.asStateFlow()

    val bypass: StateFlow<FocusBypass?> = c.settings.map { it?.focusBypass }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val focusDndEnabled: StateFlow<Boolean?> = c.settings.map { it?.focusDndEnabled }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isDndAccessGranted: Boolean get() = c.dndManager.isAccessGranted
    fun dndSettingsIntent() = c.dndManager.dndSettingsIntent()

    private var ticker: Job? = null

    fun setDraft(config: FocusConfig) {
        // Keep break <= focus while the user drags focus down.
        _draft.value = config.copy(breakMinutes = config.breakMinutes.coerceAtMost(config.focusMinutes))
    }

    fun setBypass(b: FocusBypass) = viewModelScope.launch { c.settingsRepository.setFocusBypass(b) }

    fun setFocusDndEnabled(enabled: Boolean) = viewModelScope.launch {
        c.settingsRepository.setFocusDndEnabled(enabled)
        if (!enabled) {
            c.dndManager.restoreDnd()
        } else if (_state.value.isRunning && _state.value.phase == FocusSessionStatus.FOCUSING) {
            c.dndManager.applyFocusDnd()
        }
    }

    fun start() = start(_draft.value)

    /** Debug builds only: 20 s / 10 s session to exercise phase changes, chime and notifications fast. */
    fun startQuickTest() = start(FocusState.QUICK_TEST)

    private fun start(config: FocusConfig) {
        if (!config.isValid) return
        transition(FocusState().start(config, clock()))
        c.events.log(HabitEventType.FOCUS_STARTED, metadata = mapOf("focus" to "${config.focusMinutes}", "break" to "${config.breakMinutes}", "cycles" to "${config.cycles}"))
    }

    fun pause() { transition(_state.value.pause(clock())); c.events.log(HabitEventType.FOCUS_PAUSED) }
    fun resume() { transition(_state.value.resume(clock())); c.events.log(HabitEventType.FOCUS_RESUMED) }
    fun skipBreak() = transition(_state.value.skipBreak(clock()))

    fun end() {
        val before = _state.value
        val now = clock()
        val partial = if (before.phase == FocusSessionStatus.FOCUSING) elapsedMinutes(before, now) else 0
        transition(before.cancel(now))
        c.events.log(HabitEventType.FOCUS_CANCELLED, metadata = mapOf(HabitEvent.KEY_FOCUS_MINUTES to "$partial"))
    }

    fun reset() = transition(FocusState(config = _draft.value))

    private fun transition(next: FocusState) {
        val prev = _state.value
        _state.value = next
        _now.value = clock()
        updateDnd(next)
        onPhaseChange(prev, next)
        c.notifier.showFocusOngoing(next, clock())
        if (next.isRunning) ensureTicker() else { ticker?.cancel(); ticker = null }
    }

    private fun updateDnd(state: FocusState) {
        val shouldDnd = (focusDndEnabled.value ?: true) && state.isRunning && state.phase == FocusSessionStatus.FOCUSING
        if (shouldDnd) {
            c.dndManager.applyFocusDnd()
        } else {
            c.dndManager.restoreDnd()
        }
    }

    private fun ensureTicker() {
        if (ticker?.isActive == true) return
        ticker = viewModelScope.launch {
            while (isActive) {
                delay(TICK_MILLIS)
                val now = clock()
                _now.value = now
                val prev = _state.value
                val next = prev.advance(now)
                if (next != prev) transition(next)
            }
        }
    }

    /** Logs + notifies on natural phase boundaries (not on user pause/resume). */
    private fun onPhaseChange(prev: FocusState, next: FocusState) {
        if (prev.phase == next.phase && prev.cycle == next.cycle && next.status != FocusSessionStatus.COMPLETED) return
        val focusMin = "${prev.config.focusMillis / FocusState.MINUTE}"
        when {
            next.status == FocusSessionStatus.COMPLETED && prev.status != FocusSessionStatus.COMPLETED -> {
                c.events.log(HabitEventType.FOCUS_COMPLETED, metadata = mapOf(HabitEvent.KEY_FOCUS_MINUTES to focusMin))
                c.notifier.focusAlert(FocusAlert.COMPLETED, next)
                c.notifier.playBreakTone()
            }
            prev.phase == FocusSessionStatus.FOCUSING && next.status == FocusSessionStatus.BREAK -> {
                c.events.log(HabitEventType.BREAK_STARTED, metadata = mapOf(HabitEvent.KEY_FOCUS_MINUTES to focusMin))
                c.notifier.focusAlert(FocusAlert.BREAK_STARTED, next)
                c.notifier.playBreakTone()
            }
            prev.phase == FocusSessionStatus.BREAK && next.phase == FocusSessionStatus.FOCUSING -> {
                val spent = elapsedMinutes(prev, clock()).coerceAtMost((prev.config.breakMillis / FocusState.MINUTE).toInt())
                c.events.log(HabitEventType.BREAK_COMPLETED, metadata = mapOf(HabitEvent.KEY_BREAK_MINUTES to "$spent"))
                c.notifier.focusAlert(FocusAlert.FOCUS_RESUMED, next)
            }
            prev.phase == FocusSessionStatus.FOCUSING && next.phase == FocusSessionStatus.FOCUSING && next.cycle > prev.cycle -> {
                c.events.log(HabitEventType.CYCLE_COMPLETED, metadata = mapOf(HabitEvent.KEY_FOCUS_MINUTES to focusMin))
            }
        }
    }

    private fun elapsedMinutes(s: FocusState, now: Long) = ((s.phaseMillis() - s.remaining(now)) / FocusState.MINUTE).toInt()

    override fun onCleared() {
        c.notifier.cancelFocusOngoing()
        c.dndManager.restoreDnd()
    }

    private companion object {
        const val TICK_MILLIS = 250L
    }
}
