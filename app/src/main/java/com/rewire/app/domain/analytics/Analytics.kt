package com.rewire.app.domain.analytics

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class HabitEventType {
    APP_OPENED, WARNING_SHOWN, APP_CONTINUED, APP_BLOCKED, OVERRIDE_USED, WENT_BACK,
    FOCUS_STARTED, FOCUS_PAUSED, FOCUS_RESUMED, FOCUS_COMPLETED, FOCUS_CANCELLED,
    BREAK_STARTED, BREAK_COMPLETED, CYCLE_COMPLETED,
}

data class HabitEvent(
    val id: String,
    val type: HabitEventType,
    val packageName: String?,
    val habitId: String?,
    val timestamp: Long,
    val metadata: Map<String, String> = emptyMap(),
) {
    companion object {
        /** metadata keys: minutes actually spent in the focus / break phase that just ended. */
        const val KEY_FOCUS_MINUTES = "focus_minutes"
        const val KEY_BREAK_MINUTES = "break_minutes"
    }
}

data class DailyMetrics(
    val date: LocalDate,
    val focusMinutes: Int,
    val breakMinutes: Int,
    val blockedAttempts: Int,
    val warningCount: Int,
    val overrideCount: Int,
    val wentBackCount: Int,
    val sessionsCompleted: Int,
) {
    /** Share of friction moments where user chose control. Null when nothing happened yet. */
    val disciplineScore: Float?
        get() {
            val moments = warningCount + blockedAttempts
            if (moments == 0) return null
            return ((moments - overrideCount).coerceAtLeast(0)).toFloat() / moments
        }
}

object MetricsCalculator {
    fun daily(events: List<HabitEvent>, date: LocalDate, zone: ZoneId): DailyMetrics {
        val day = events.filter { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() == date }
        fun count(t: HabitEventType) = day.count { it.type == t }
        fun minutes(key: String) = day.sumOf { it.metadata[key]?.toIntOrNull() ?: 0 }
        return DailyMetrics(
            date = date,
            focusMinutes = minutes(HabitEvent.KEY_FOCUS_MINUTES),
            breakMinutes = minutes(HabitEvent.KEY_BREAK_MINUTES),
            blockedAttempts = count(HabitEventType.APP_BLOCKED),
            warningCount = count(HabitEventType.WARNING_SHOWN),
            overrideCount = count(HabitEventType.OVERRIDE_USED),
            wentBackCount = count(HabitEventType.WENT_BACK),
            sessionsCompleted = count(HabitEventType.FOCUS_COMPLETED),
        )
    }

    fun lastDays(events: List<HabitEvent>, today: LocalDate, days: Int, zone: ZoneId): List<DailyMetrics> =
        (days - 1 downTo 0).map { daily(events, today.minusDays(it.toLong()), zone) }
}
