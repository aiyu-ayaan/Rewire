package com.aiyu.rewire.domain.restriction

import com.aiyu.rewire.domain.habit.HabitProfile
import com.aiyu.rewire.domain.habit.RestrictionRule
import com.aiyu.rewire.domain.habit.WarningLevel

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
    /** Foreground minutes the daily limit counts (window time only when a window is set) across the habit's apps; null = usage access not granted. */
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

        val breach = when {
            start != null && end != null && !inWindow(i.nowMinutes, start, end) -> BlockReason.OUTSIDE_WINDOW
            launches != null && i.launchesToday >= launches -> BlockReason.LAUNCH_LIMIT
            limit != null && i.usageMinutesToday != null && i.usageMinutesToday >= limit -> BlockReason.DAILY_LIMIT
            else -> null
        }
        // Boundaries are the trigger, the level is the response. No boundaries means "every open".
        val hasBoundary = (start != null && end != null) || limit != null || launches != null
        return when (rule.warningLevel) {
            WarningLevel.MINOR, WarningLevel.MAJOR ->
                if (breach != null || !hasBoundary) RestrictionDecision.Warn(rule.warningLevel) else RestrictionDecision.Allow
            WarningLevel.MAX -> when {
                breach != null -> RestrictionDecision.Block(breach)
                // Max with no boundaries at all means "never": the user asked for a hard block.
                !hasBoundary -> RestrictionDecision.Block(BlockReason.ALWAYS)
                else -> RestrictionDecision.Allow
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

    /**
     * Where daily-limit usage starts counting, as minutes since local midnight (negative = yesterday).
     * With an allowed window it is the latest window start at or before [nowMinutes], so only time
     * spent inside the window counts; otherwise null = the whole day.
     */
    fun limitCountsFromMinutes(rule: RestrictionRule, nowMinutes: Int): Int? {
        val start = rule.allowedStartMinutes ?: return null
        if (rule.allowedEndMinutes == null || rule.allowedEndMinutes == start) return null
        return if (start <= nowMinutes) start else start - MINUTES_PER_DAY
    }

    /** [start, end) in minutes; supports overnight windows like 22:00–06:00. */
    fun inWindow(now: Int, start: Int, end: Int): Boolean = when {
        start == end -> true
        start < end -> now in start until end
        else -> now >= start || now < end
    }

    private const val MINUTES_PER_DAY = 24 * 60
}
