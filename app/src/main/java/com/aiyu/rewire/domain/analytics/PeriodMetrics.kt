package com.aiyu.rewire.domain.analytics

import com.aiyu.rewire.domain.goals.GoalRules
import com.aiyu.rewire.domain.goals.Goals
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** One chart point: a value on a calendar day. */
data class TrendPoint(val date: LocalDate, val value: Int)

/** A protected app and how many times it was opened. */
data class AppCount(val packageName: String, val opens: Int)

/**
 * Chart-ready week. [days] always holds exactly 7 entries starting at [weekStart] (empty days are zeroed),
 * so a chart can plot it without gap handling.
 */
data class WeeklyMetrics(
    val weekStart: LocalDate,
    val days: List<DailyMetrics>,
    val focusMinutes: Int,
    /** Sum of the supplied per-day screen time (protected-app time when the caller passes that). */
    val distractedMinutes: Int,
    /** Guard friction moments: warnings shown + blocked attempts. */
    val habitAttempts: Int,
    /** Times the user chose control: went back, plus Max blocks that were not overridden. */
    val successfulBlocks: Int,
    val mostOpenedApp: AppCount?,
    /** Hour of day (0-23) with the most warnings/blocks in the week, null when none. */
    val mostProblematicHour: Int?,
    /** Longest single focus phase (minutes) in the week. */
    val longestFocusMinutes: Int,
) {
    val focusHours: Float get() = focusMinutes / 60f
    val distractedHours: Float get() = distractedMinutes / 60f
}

/**
 * Chart-ready month. [days] covers day 1 through the last day (the whole month, or up to today for the current one).
 * Ratios are 0..1, or null when there is nothing to compare yet.
 */
data class MonthlyMetrics(
    val month: YearMonth,
    val days: List<DailyMetrics>,
    val focusTrend: List<TrendPoint>,
    val screenTimeTrend: List<TrendPoint>,
    val overrideTrend: List<TrendPoint>,
    /** Drop in friction moments, second half of the period vs the first (positive = fewer). Null if the first half had none. */
    val habitReduction: Float?,
    /** Share of days on track: some focus, or at least half of Guard friction handled without an override. */
    val consistency: Float,
    /** Share of days meeting the user's goals; with none set, share of days with a completed focus session. */
    val goalCompletion: Float,
)

/** Pure weekly/monthly aggregation over the event log. Stable public API for Matrix. */
object PeriodMetrics {

    /** First day of the week containing [date]. */
    fun weekStart(date: LocalDate, firstDay: DayOfWeek = DayOfWeek.MONDAY): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(firstDay))

    /**
     * Metrics for the 7 days from [weekStart]. [screenTimeMinutes] supplies per-day usage (from UsageStats,
     * which is not in the event log); days missing from it count as 0.
     */
    fun weekly(
        events: List<HabitEvent>,
        weekStart: LocalDate,
        zone: ZoneId,
        screenTimeMinutes: Map<LocalDate, Int> = emptyMap(),
    ): WeeklyMetrics {
        val days = days(events, weekStart, 7, zone, screenTimeMinutes)
        val week = events.filter {
            val d = Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate()
            !d.isBefore(weekStart) && d.isBefore(weekStart.plusDays(7))
        }
        val mostOpened = week.filter { it.type == HabitEventType.APP_OPENED && it.packageName != null }
            .groupingBy { it.packageName!! }.eachCount()
            .maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenByDescending { it.key })
            ?.let { AppCount(it.key, it.value) }
        val problem = week.filter { it.type == HabitEventType.WARNING_SHOWN || it.type == HabitEventType.APP_BLOCKED }
            .groupingBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).hour }.eachCount()
            .maxWithOrNull(compareBy<Map.Entry<Int, Int>> { it.value }.thenByDescending { it.key })?.key
        return WeeklyMetrics(
            weekStart = weekStart,
            days = days,
            focusMinutes = days.sumOf { it.focusMinutes },
            distractedMinutes = days.sumOf { it.screenTimeMinutes },
            habitAttempts = days.sumOf { it.frictionMoments },
            successfulBlocks = days.sumOf { it.wentBackCount + (it.blockedAttempts - it.overrideCount).coerceAtLeast(0) },
            mostOpenedApp = mostOpened,
            mostProblematicHour = problem,
            longestFocusMinutes = week.maxOfOrNull { it.metadata[HabitEvent.KEY_FOCUS_MINUTES]?.toIntOrNull() ?: 0 } ?: 0,
        )
    }

    /** Metrics for [month], capped at [today] so unfinished months are not diluted by future days. */
    fun monthly(
        events: List<HabitEvent>,
        month: YearMonth,
        today: LocalDate,
        zone: ZoneId,
        screenTimeMinutes: Map<LocalDate, Int> = emptyMap(),
        goals: Goals = Goals(),
    ): MonthlyMetrics {
        val first = month.atDay(1)
        val last = minOf(month.atEndOfMonth(), today)
        val count = if (last.isBefore(first)) 0 else (last.toEpochDay() - first.toEpochDay() + 1).toInt()
        val days = days(events, first, count, zone, screenTimeMinutes)
        val half = count / 2
        val early = days.take(half).sumOf { it.frictionMoments }
        val late = days.drop(half).sumOf { it.frictionMoments }
        return MonthlyMetrics(
            month = month,
            days = days,
            focusTrend = days.map { TrendPoint(it.date, it.focusMinutes) },
            screenTimeTrend = days.map { TrendPoint(it.date, it.screenTimeMinutes) },
            overrideTrend = days.map { TrendPoint(it.date, it.overrideCount) },
            habitReduction = if (early == 0) null else (early - late).toFloat() / early,
            consistency = ratio(days, ::dayOnTrack),
            goalCompletion = if (goals.isEmpty) ratio(days) { it.sessionsCompleted > 0 } else ratio(days) { GoalRules.met(it, goals) },
        )
    }

    /** The "on track" day rule: some focus, or at least half of Guard friction handled without an override. */
    fun dayOnTrack(d: DailyMetrics) = d.focusMinutes > 0 || (d.disciplineScore ?: 0f) >= 0.5f

    private fun ratio(days: List<DailyMetrics>, ok: (DailyMetrics) -> Boolean) =
        if (days.isEmpty()) 0f else days.count(ok).toFloat() / days.size

    private fun days(events: List<HabitEvent>, start: LocalDate, count: Int, zone: ZoneId, screen: Map<LocalDate, Int>) =
        (0 until count).map { i ->
            val d = start.plusDays(i.toLong())
            MetricsCalculator.daily(events, d, zone).copy(screenTimeMinutes = screen[d] ?: 0)
        }
}
