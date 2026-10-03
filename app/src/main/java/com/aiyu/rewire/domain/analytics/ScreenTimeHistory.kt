package com.aiyu.rewire.domain.analytics

import java.time.LocalDate

/** Pure rules for the stored per-day screen-time snapshots (UsageStats only remembers about a week). */
object ScreenTimeHistory {
    const val KEEP_DAYS = 62L

    /** A day's screen time only grows, so a later (or stale, partial) reading never lowers a stored one. */
    fun merge(stored: Map<LocalDate, Int>, date: LocalDate, minutes: Int, today: LocalDate): Map<LocalDate, Int> {
        val cutoff = today.minusDays(KEEP_DAYS)
        val next = stored.toMutableMap()
        next[date] = maxOf(stored[date] ?: 0, minutes.coerceAtLeast(0))
        return next.filterKeys { it >= cutoff }
    }

    /** Days in [today-6, today] that still need a reading: today always (it is still growing), past days only when missing. */
    fun daysToSync(stored: Map<LocalDate, Int>, today: LocalDate): List<LocalDate> =
        (6L downTo 0L).map { today.minusDays(it) }.filter { it == today || it !in stored }
}
