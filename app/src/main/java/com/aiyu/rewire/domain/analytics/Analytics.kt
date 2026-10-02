package com.rewire.app.domain.analytics

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class HabitEventType {
    APP_OPENED, WARNING_SHOWN, APP_CONTINUED, APP_BLOCKED, OVERRIDE_USED, WENT_BACK,
    FOCUS_STARTED, FOCUS_PAUSED, FOCUS_RESUMED, FOCUS_COMPLETED, FOCUS_CANCELLED,
    BREAK_STARTED, BREAK_COMPLETED, CYCLE_COMPLETED, NOTIFICATION_BLOCKED,
}

@Serializable
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
    val continuedCount: Int = 0,
    val appOpens: Int = 0,
    val notificationsBlocked: Int = 0,
    val screenTimeMinutes: Int = 0,
) {
    /** Times Guard stepped in (warning or block). */
    val frictionMoments: Int get() = warningCount + blockedAttempts

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
            continuedCount = count(HabitEventType.APP_CONTINUED),
            appOpens = count(HabitEventType.APP_OPENED),
            notificationsBlocked = count(HabitEventType.NOTIFICATION_BLOCKED),
        )
    }

    /** Guard friction grouped by [key] (habit id or package), busiest first. */
    fun breakdown(events: List<HabitEvent>, since: Long, key: (HabitEvent) -> String?): List<GuardBreakdown> =
        events.filter { it.timestamp >= since && it.type in GUARD_TYPES }
            .mapNotNull { e -> key(e)?.let { it to e } }
            .groupBy({ it.first }, { it.second })
            .map { (k, list) ->
                fun c(t: HabitEventType) = list.count { it.type == t }
                GuardBreakdown(
                    key = k,
                    moments = c(HabitEventType.WARNING_SHOWN) + c(HabitEventType.APP_BLOCKED),
                    wentBack = c(HabitEventType.WENT_BACK),
                    overrides = c(HabitEventType.OVERRIDE_USED),
                    opens = c(HabitEventType.APP_OPENED),
                )
            }
            .sortedByDescending { it.moments + it.opens }

    /** Hour of day (0-23) with the most guard friction since [since], or null if none. */
    fun peakHour(events: List<HabitEvent>, since: Long, zone: ZoneId): Int? =
        events.filter { it.timestamp >= since && (it.type == HabitEventType.WARNING_SHOWN || it.type == HabitEventType.APP_BLOCKED) }
            .groupingBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).hour }.eachCount()
            .maxByOrNull { it.value }?.key

    private val GUARD_TYPES = setOf(
        HabitEventType.WARNING_SHOWN, HabitEventType.APP_BLOCKED, HabitEventType.WENT_BACK,
        HabitEventType.OVERRIDE_USED, HabitEventType.APP_OPENED,
    )

    fun lastDays(events: List<HabitEvent>, today: LocalDate, days: Int, zone: ZoneId): List<DailyMetrics> =
        (days - 1 downTo 0).map { daily(events, today.minusDays(it.toLong()), zone) }
}

data class GuardBreakdown(val key: String, val moments: Int, val wentBack: Int, val overrides: Int, val opens: Int)

/**
 * Light-hearted comparisons for the Matrix header, like "that's 3 movies of focus".
 * Deterministic per day so the line doesn't flicker on every recomposition.
 */
object Punchlines {
    private class Measure(val minutes: Int, val one: String, val many: String)

    private val focusUnits = listOf(
        Measure(22, "sitcom episode", "sitcom episodes"),
        Measure(120, "feature film", "feature films"),
        Measure(4, "pop song", "pop songs"),
        Measure(43, "football half", "football halves"),
        Measure(540, "Lord of the Rings extended trilogy", "Lord of the Rings extended trilogies"),
        Measure(25, "pomodoro", "pomodoros"),
        Measure(180, "Great Gatsby audiobook", "Great Gatsby audiobooks"),
        Measure(121, "world-record marathon", "world-record marathons"),
    )

    fun focus(weekFocusMinutes: Int, seed: Long): String? {
        if (weekFocusMinutes <= 0) return null
        val fits = focusUnits.filter { weekFocusMinutes >= it.minutes }.ifEmpty { return "Every minute counts. ${weekFocusMinutes}m of focus is a real start." }
        val u = fits[Math.floorMod(seed, fits.size.toLong()).toInt()]
        val n = weekFocusMinutes / u.minutes
        val x = if (n == 1) "one ${u.one}" else "~$n ${u.many}"
        return "Your focus this week ≈ $x. Except this time you made something."
    }

    fun guard(wentBack: Int, overrides: Int, seed: Long): String? {
        val lines = when {
            wentBack == 0 && overrides == 0 -> return null
            wentBack >= overrides * 3 && wentBack > 0 -> listOf(
                "You chose to go back $wentBack times. That's $wentBack small wins nobody saw.",
                "$wentBack reflexes interrupted. Your thumb is learning new tricks.",
                "Went back $wentBack times. The algorithm misses you. You don't miss it.",
            )
            wentBack >= overrides -> listOf(
                "More go-backs than overrides. The balance is tipping your way.",
                "$wentBack back, $overrides through. Control is winning on points.",
            )
            else -> listOf(
                "$overrides overrides this week. No shame, just data. Patterns show up here first.",
                "The apps won a few rounds. Matrix keeps score so you can plan the rematch.",
            )
        }
        return lines[Math.floorMod(seed, lines.size.toLong()).toInt()]
    }
}
