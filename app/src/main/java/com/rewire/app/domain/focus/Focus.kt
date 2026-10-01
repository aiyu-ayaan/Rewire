package com.rewire.app.domain.focus

enum class FocusSessionStatus { IDLE, FOCUSING, BREAK, PAUSED, COMPLETED, CANCELLED }

enum class FocusConfigError { FOCUS_TOO_SHORT, BREAK_TOO_SHORT, BREAK_LONGER_THAN_FOCUS, CYCLES_TOO_FEW }

data class FocusConfig(
    val focusMinutes: Int,
    val breakMinutes: Int,
    val cycles: Int,
    /** Length of one "minute". Only debug quick-test shrinks it; real sessions keep [FocusState.MINUTE]. */
    val unitMillis: Long = FocusState.MINUTE,
) {
    val focusMillis get() = focusMinutes * unitMillis
    val breakMillis get() = breakMinutes * unitMillis

    fun validate(): FocusConfigError? = when {
        focusMinutes < 1 -> FocusConfigError.FOCUS_TOO_SHORT
        breakMinutes < 0 -> FocusConfigError.BREAK_TOO_SHORT
        breakMinutes > focusMinutes -> FocusConfigError.BREAK_LONGER_THAN_FOCUS
        cycles < 1 -> FocusConfigError.CYCLES_TOO_FEW
        else -> null
    }

    val isValid get() = validate() == null
}

/**
 * Immutable focus session state. All transitions are pure and take `now` (epoch millis),
 * so the same machine can later run inside a ForegroundService and be restored from storage.
 */
data class FocusState(
    val status: FocusSessionStatus = FocusSessionStatus.IDLE,
    val config: FocusConfig = FocusConfig(25, 5, 4),
    val cycle: Int = 1,
    /** End of current phase while running; null when idle/paused/finished. */
    val phaseEndsAt: Long? = null,
    /** Remaining millis frozen while paused. */
    val pausedRemaining: Long? = null,
    /** FOCUSING or BREAK — what PAUSED resumes into. */
    val pausedFrom: FocusSessionStatus? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
) {
    val isRunning get() = status == FocusSessionStatus.FOCUSING || status == FocusSessionStatus.BREAK
    val isActive get() = isRunning || status == FocusSessionStatus.PAUSED

    /** Phase being timed: FOCUSING/BREAK even while paused. */
    val phase: FocusSessionStatus? get() = if (status == FocusSessionStatus.PAUSED) pausedFrom else status.takeIf { isRunning }

    fun phaseMillis(): Long = when (phase) {
        FocusSessionStatus.BREAK -> config.breakMillis
        else -> config.focusMillis
    }

    fun remaining(now: Long): Long = when {
        status == FocusSessionStatus.PAUSED -> pausedRemaining ?: 0
        phaseEndsAt != null -> (phaseEndsAt - now).coerceAtLeast(0)
        else -> 0
    }

    /** 0..1 of current phase elapsed. */
    fun progress(now: Long): Float {
        val total = phaseMillis()
        return if (total == 0L) 1f else 1f - remaining(now).toFloat() / total
    }

    /**
     * Deep-work time done so far: finished focus blocks plus the elapsed part of the current one.
     * Call on the live state; a cancelled state no longer knows its phase, so read it before [cancel].
     */
    fun focusedMillis(now: Long): Long = when {
        status == FocusSessionStatus.COMPLETED -> config.cycles * config.focusMillis
        !isActive -> 0
        phase == FocusSessionStatus.BREAK -> cycle * config.focusMillis
        else -> (cycle - 1) * config.focusMillis + (config.focusMillis - remaining(now))
    }

    fun start(config: FocusConfig, now: Long): FocusState {
        require(config.isValid) { "Invalid focus config: ${config.validate()}" }
        return FocusState(
            status = FocusSessionStatus.FOCUSING,
            config = config,
            cycle = 1,
            phaseEndsAt = now + config.focusMillis,
            startedAt = now,
        )
    }

    fun pause(now: Long): FocusState {
        if (!isRunning) return this
        return copy(status = FocusSessionStatus.PAUSED, pausedRemaining = remaining(now), pausedFrom = status, phaseEndsAt = null)
    }

    fun resume(now: Long): FocusState {
        if (status != FocusSessionStatus.PAUSED) return this
        return copy(status = pausedFrom!!, phaseEndsAt = now + pausedRemaining!!, pausedRemaining = null, pausedFrom = null)
    }

    fun cancel(now: Long): FocusState =
        if (!isActive) this else copy(status = FocusSessionStatus.CANCELLED, phaseEndsAt = null, pausedRemaining = null, pausedFrom = null, completedAt = now)

    fun skipBreak(now: Long): FocusState = if (phase == FocusSessionStatus.BREAK) nextPhase(now) else this

    /** Advances past every phase boundary already reached. Call on each tick. */
    fun advance(now: Long): FocusState {
        var s = this
        while (s.isRunning && s.phaseEndsAt != null && now >= s.phaseEndsAt) s = s.nextPhase(s.phaseEndsAt)
        return s
    }

    /** [at] = moment current phase ended; next phase is timed from there (no drift on late ticks). */
    private fun nextPhase(at: Long): FocusState {
        val fromBreak = phase == FocusSessionStatus.BREAK
        return when {
            fromBreak -> copy(status = FocusSessionStatus.FOCUSING, cycle = cycle + 1, phaseEndsAt = at + config.focusMillis, pausedRemaining = null, pausedFrom = null)
            cycle >= config.cycles -> copy(status = FocusSessionStatus.COMPLETED, phaseEndsAt = null, completedAt = at)
            config.breakMinutes == 0 -> copy(status = FocusSessionStatus.FOCUSING, cycle = cycle + 1, phaseEndsAt = at + config.focusMillis)
            else -> copy(status = FocusSessionStatus.BREAK, phaseEndsAt = at + config.breakMillis)
        }
    }

    companion object {
        const val MINUTE = 60_000L

        /** Debug-only: 20 s focus / 10 s break, 2 cycles. */
        val QUICK_TEST = FocusConfig(focusMinutes = 2, breakMinutes = 1, cycles = 2, unitMillis = 10_000)
    }
}

/** One focus session as stored: live while active, history once COMPLETED / CANCELLED. */
data class FocusSession(
    val id: String,
    val state: FocusState,
    /** Deep-work time done, frozen when the session ends. */
    val focusedMillis: Long,
    /** Optional "what I achieved", written after the session ends. */
    val note: String? = null,
) {
    val isFinished get() = state.status == FocusSessionStatus.COMPLETED || state.status == FocusSessionStatus.CANCELLED
}
