package com.rewire.app.domain.restriction

import com.rewire.app.domain.habit.HabitProfile
import com.rewire.app.domain.habit.WarningLevel

enum class BlockReason { OUTSIDE_WINDOW, LAUNCH_LIMIT, DAILY_LIMIT, ALWAYS }

sealed interface RestrictionDecision {
    data object Allow : RestrictionDecision
    data class Warn(val level: WarningLevel) : RestrictionDecision
    data class Block(val reason: BlockReason) : RestrictionDecision
}

/** Everything the engine needs, already measured. No Android, no clock reads. */
data class RuleInput(
    val profile: HabitProfile,
    /** Minutes since local midnight. */
    val nowMinutes: Int,
    /** Opens let through today (APP_OPENED events for this habit). */
    val launchesToday: Int,
    /** Foreground minutes today across the habit's apps; null = usage access not granted. */
    val usageMinutesToday: Int?,
    val focusing: Boolean,
    val bypassMinor: Boolean,
    val bypassMajor: Boolean,
    val bypassMax: Boolean,
)

/** Single place that turns rules into a decision (CLAUDE.md §25). */
object RuleEngine {

    fun decide(i: RuleInput): RestrictionDecision {
        val rule = i.profile.rule
        if (!i.profile.habit.enabled) return RestrictionDecision.Allow
        val bypass = i.focusing && when (rule.warningLevel) {
            WarningLevel.MINOR -> i.bypassMinor
            WarningLevel.MAJOR -> i.bypassMajor
            WarningLevel.MAX -> i.bypassMax
        }
        if (bypass) return RestrictionDecision.Allow

        val r = i.profile.rule
        val start = r.allowedStartMinutes
        val end = r.allowedEndMinutes
        val limit = r.dailyLimitMinutes
        val launches = r.maxLaunches

        // 1. Any configured boundary breached -> hard block
        if (start != null && end != null && !inWindow(i.nowMinutes, start, end)) {
            return RestrictionDecision.Block(BlockReason.OUTSIDE_WINDOW)
        }
        if (launches != null && i.launchesToday >= launches) {
            return RestrictionDecision.Block(BlockReason.LAUNCH_LIMIT)
        }
        if (limit != null && i.usageMinutesToday != null && i.usageMinutesToday >= limit) {
            return RestrictionDecision.Block(BlockReason.DAILY_LIMIT)
        }

        // 2. Within boundaries (or no boundaries configured) -> apply configured friction level
        return when (rule.warningLevel) {
            WarningLevel.MINOR -> RestrictionDecision.Warn(WarningLevel.MINOR)
            WarningLevel.MAJOR -> RestrictionDecision.Warn(WarningLevel.MAJOR)
            WarningLevel.MAX -> {
                // Max with no boundaries at all means "never": the user asked for a hard block.
                if (start == null && end == null && limit == null && launches == null) {
                    RestrictionDecision.Block(BlockReason.ALWAYS)
                } else {
                    RestrictionDecision.Allow
                }
            }
        }
    }

    /**
     * Minutes until an allowed session crosses a boundary (window end or daily limit),
     * so the monitor can re-check exactly then instead of polling. Null = nothing to wait for.
     */
    fun minutesUntilNextBoundary(i: RuleInput): Int? {
        val r = i.profile.rule
        if (!i.profile.habit.enabled) return null
        val candidates = buildList {
            if (r.allowedStartMinutes != null && r.allowedEndMinutes != null && inWindow(i.nowMinutes, r.allowedStartMinutes, r.allowedEndMinutes)) {
                add(Math.floorMod(r.allowedEndMinutes - i.nowMinutes, MINUTES_PER_DAY))
            }
            if (r.dailyLimitMinutes != null && i.usageMinutesToday != null) add((r.dailyLimitMinutes - i.usageMinutesToday).coerceAtLeast(0))
        }
        return candidates.minOrNull()
    }

    /** [start, end) in minutes; supports overnight windows like 22:00–06:00. */
    fun inWindow(now: Int, start: Int, end: Int): Boolean = when {
        start == end -> true
        start < end -> now in start until end
        else -> now >= start || now < end
    }

    private const val MINUTES_PER_DAY = 24 * 60
}
