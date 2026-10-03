package com.aiyu.rewire.feature.matrix

import com.aiyu.rewire.domain.analytics.DailyMetrics
import com.aiyu.rewire.domain.analytics.HabitEvent
import com.aiyu.rewire.domain.analytics.HabitEventType
import com.aiyu.rewire.domain.analytics.MonthlyMetrics
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Chart-ready data. Charts draw these and nothing else; mapping from domain metrics is in [ChartMapper]. */
data class ChartPoint(val label: String, val value: Float)

data class LineSeries(val name: String, val points: List<ChartPoint>)

data class StackSeries(val name: String, val values: List<Int>)

/** [labels] are the x categories; every series has one value per label. */
data class StackedBarData(val labels: List<String>, val series: List<StackSeries>) {
    val totals: List<Int> get() = labels.indices.map { i -> series.sumOf { it.values.getOrElse(i) { 0 } } }
    val isEmpty: Boolean get() = totals.all { it == 0 }
}

/** [fraction] is 0..1 on the radar; [display] is the human value shown in the table. */
data class RadarAxis(val label: String, val fraction: Float, val display: String)

data class ScatterPoint(val label: String, val x: Float, val y: Float)

/** [level] is 0..1 relative to the busiest cell; [value] is the raw number. */
data class HeatCell(val label: String, val dayOfMonth: Int, val level: Float, val value: Int)

enum class TimelineKind { FOCUS_START, FOCUS_DONE, WARNING, BLOCKED, OVERRIDE, WENT_BACK }

data class TimelineItem(val minuteOfDay: Int, val kind: TimelineKind)

object ChartMapper {
    fun trend(days: List<DailyMetrics>, label: (LocalDate) -> String, value: (DailyMetrics) -> Int): List<ChartPoint> =
        days.map { ChartPoint(label(it.date), value(it).toFloat()) }

    /** [names] order: went back, continued, blocked, overrides. */
    fun stackedGuard(days: List<DailyMetrics>, label: (LocalDate) -> String, names: List<String>): StackedBarData {
        require(names.size == 4)
        val picks = listOf<(DailyMetrics) -> Int>({ it.wentBackCount }, { it.continuedCount }, { it.blockedAttempts }, { it.overrideCount })
        return StackedBarData(
            days.map { label(it.date) },
            names.mapIndexed { i, n -> StackSeries(n, days.map(picks[i])) },
        )
    }

    fun heat(days: List<DailyMetrics>, label: (LocalDate) -> String, value: (DailyMetrics) -> Int): List<HeatCell> {
        val max = days.maxOfOrNull(value)?.coerceAtLeast(1) ?: 1
        return days.map { HeatCell(label(it.date), it.date.dayOfMonth, value(it).toFloat() / max, value(it)) }
    }

    /** Focus minutes (x) against friction moments (y); days with neither are left out. */
    fun scatter(days: List<DailyMetrics>, label: (LocalDate) -> String): List<ScatterPoint> =
        days.filter { it.focusMinutes > 0 || it.frictionMoments > 0 }
            .map { ScatterPoint(label(it.date), it.focusMinutes.toFloat(), it.frictionMoments.toFloat()) }

    /** Five monthly "control" ratios, each clamped to 0..1. [names] order: consistency, goals, reduction, discipline, focus days. */
    fun radar(m: MonthlyMetrics, names: List<String>): List<RadarAxis> {
        require(names.size == 5)
        val scores = m.days.mapNotNull { it.disciplineScore }
        val values = listOf(
            m.consistency,
            m.goalCompletion,
            m.habitReduction ?: 0f,
            if (scores.isEmpty()) 0f else scores.average().toFloat(),
            if (m.days.isEmpty()) 0f else m.days.count { it.focusMinutes > 0 }.toFloat() / m.days.size,
        ).map { it.coerceIn(0f, 1f) }
        return names.zip(values) { n, v -> RadarAxis(n, v, "${(v * 100).toInt()}%") }
    }

    /** Guard and focus moments of [date], in time order. */
    fun timeline(events: List<HabitEvent>, date: LocalDate, zone: ZoneId): List<TimelineItem> =
        events.mapNotNull { e ->
            val at = Instant.ofEpochMilli(e.timestamp).atZone(zone)
            if (at.toLocalDate() != date) return@mapNotNull null
            val kind = when (e.type) {
                HabitEventType.FOCUS_STARTED -> TimelineKind.FOCUS_START
                HabitEventType.FOCUS_COMPLETED -> TimelineKind.FOCUS_DONE
                HabitEventType.WARNING_SHOWN -> TimelineKind.WARNING
                HabitEventType.APP_BLOCKED -> TimelineKind.BLOCKED
                HabitEventType.OVERRIDE_USED -> TimelineKind.OVERRIDE
                HabitEventType.WENT_BACK -> TimelineKind.WENT_BACK
                else -> return@mapNotNull null
            }
            TimelineItem(at.hour * 60 + at.minute, kind)
        }.sortedBy { it.minuteOfDay }
}
