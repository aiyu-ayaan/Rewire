package com.aiyu.rewire.domain.analytics

import java.time.LocalDate
import java.time.ZoneId

/** Decides what the once-a-day recap says. Pure: the worker only fetches events and posts the result. */
object DailySummary {
    /**
     * Recap of the day before [today], or null when nothing happened that day (no focus, no Guard moments,
     * no protected-app opens), so a quiet day sends no notification.
     */
    fun forYesterday(events: List<HabitEvent>, today: LocalDate, zone: ZoneId): DailyMetrics? =
        MetricsCalculator.daily(events, today.minusDays(1), zone).takeIf {
            it.focusMinutes > 0 || it.frictionMoments > 0 || it.appOpens > 0
        }
}
