package com.aiyu.rewire.domain.analytics

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

/** A Matrix-header comparison as data; the UI picks the wording from strings.xml. [unit] indexes [Punchlines.focusUnitMinutes], null = too little focus to compare. */
data class FocusPunch(val unit: Int?, val count: Int, val minutes: Int)

/** [variant] indexes the guard lines in strings.xml (0-2 mostly went back, 3-4 balanced, 5-6 mostly overrides). */
data class GuardPunch(val variant: Int, val wentBack: Int, val overrides: Int)

/**
 * Light-hearted comparisons for the Matrix header, like "that's 3 movies of focus".
 * Deterministic per day so the line doesn't flicker on every recomposition.
 */
object Punchlines {
    val focusUnitMinutes = listOf(22, 120, 4, 43, 540, 25, 180, 121)

    fun focus(weekFocusMinutes: Int, seed: Long): FocusPunch? {
        if (weekFocusMinutes <= 0) return null
        val fits = focusUnitMinutes.indices.filter { weekFocusMinutes >= focusUnitMinutes[it] }
        if (fits.isEmpty()) return FocusPunch(null, 0, weekFocusMinutes)
        val u = fits[Math.floorMod(seed, fits.size.toLong()).toInt()]
        return FocusPunch(u, weekFocusMinutes / focusUnitMinutes[u], weekFocusMinutes)
    }

    fun guard(wentBack: Int, overrides: Int, seed: Long): GuardPunch? {
        val variants = when {
            wentBack == 0 && overrides == 0 -> return null
            wentBack >= overrides * 3 && wentBack > 0 -> 0..2
            wentBack >= overrides -> 3..4
            else -> 5..6
        }.toList()
        return GuardPunch(variants[Math.floorMod(seed, variants.size.toLong()).toInt()], wentBack, overrides)
    }
}
