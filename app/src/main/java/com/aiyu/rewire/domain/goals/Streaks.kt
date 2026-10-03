package com.aiyu.rewire.domain.goals

import com.aiyu.rewire.domain.analytics.DailyMetrics
import com.aiyu.rewire.domain.analytics.HabitEvent
import com.aiyu.rewire.domain.analytics.MetricsCalculator
import com.aiyu.rewire.domain.analytics.PeriodMetrics
import java.time.LocalDate
import java.time.ZoneId

/** [current] ends yesterday or today; [best] is the longest run inside the window. */
data class Streak(val current: Int, val best: Int, val todayMet: Boolean)

/** What the Guard and Matrix chips show: streak, today's goal status, and whether any goal is set. */
data class DayProgress(val streak: Streak, val status: GoalStatus, val hasGoals: Boolean) {
    companion object {
        val EMPTY = DayProgress(Streak(0, 0, false), GoalStatus(0, null, 0, null), false)
    }
}

object Streaks {
    /** Window the UI looks back over. ponytail: best streak is capped to this; widen or persist if it matters. */
    const val WINDOW_DAYS = 90

    /** A day counts per goals when set; with none, by the same rule Matrix "consistency" uses. */
    fun dayMet(day: DailyMetrics, goals: Goals): Boolean =
        if (goals.isEmpty) PeriodMetrics.dayOnTrack(day) else GoalRules.met(day, goals)

    /** [days] oldest first, the last being today. Today still in progress never breaks the streak. */
    fun compute(days: List<DailyMetrics>, goals: Goals): Streak {
        val met = days.map { dayMet(it, goals) }
        var best = 0
        var run = 0
        met.forEach { run = if (it) run + 1 else 0; best = maxOf(best, run) }
        val todayMet = met.lastOrNull() == true
        var current = 0
        var i = met.lastIndex - if (todayMet) 0 else 1
        while (i >= 0 && met[i]) { current++; i-- }
        return Streak(current, best, todayMet)
    }

    fun progress(events: List<HabitEvent>, today: LocalDate, zone: ZoneId, goals: Goals, window: Int = WINDOW_DAYS): DayProgress {
        val days = MetricsCalculator.lastDays(events, today, window, zone)
        return DayProgress(compute(days, goals), GoalRules.status(days.last(), goals), !goals.isEmpty)
    }
}
