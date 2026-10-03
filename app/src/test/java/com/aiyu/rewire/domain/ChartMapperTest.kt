package com.aiyu.rewire.domain

import com.aiyu.rewire.domain.analytics.DailyMetrics
import com.aiyu.rewire.domain.analytics.HabitEvent
import com.aiyu.rewire.domain.analytics.HabitEventType
import com.aiyu.rewire.domain.analytics.PeriodMetrics
import com.aiyu.rewire.feature.matrix.ChartMapper
import com.aiyu.rewire.feature.matrix.TimelineKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class ChartMapperTest {
    private val d = LocalDate.of(2026, 9, 28)
    private fun day(i: Int, focus: Int = 0, warn: Int = 0, blocked: Int = 0, over: Int = 0, back: Int = 0) =
        DailyMetrics(d.plusDays(i.toLong()), focus, 0, blocked, warn, over, back, 0)
    private val label = { x: LocalDate -> x.dayOfMonth.toString() }

    @Test fun trendKeepsOrderAndValues() {
        val p = ChartMapper.trend(listOf(day(0, focus = 5), day(1, focus = 9)), label) { it.focusMinutes }
        assertEquals(listOf("28" to 5f, "29" to 9f), p.map { it.label to it.value })
    }

    @Test fun stackedTotalsAndEmpty() {
        val names = listOf("a", "b", "c", "d")
        val s = ChartMapper.stackedGuard(listOf(day(0, blocked = 2, over = 1, back = 3), day(1)), label, names)
        assertEquals(listOf(6, 0), s.totals)
        assertTrue(ChartMapper.stackedGuard(listOf(day(0)), label, names).isEmpty)
    }

    @Test fun heatNormalisesToBusiestDay() {
        val h = ChartMapper.heat(listOf(day(0, focus = 10), day(1, focus = 40), day(2)), label) { it.focusMinutes }
        assertEquals(listOf(0.25f, 1f, 0f), h.map { it.level })
        assertEquals(29, h[1].dayOfMonth)
        assertEquals(0f, ChartMapper.heat(listOf(day(0)), label) { it.focusMinutes }[0].level)
    }

    @Test fun scatterSkipsQuietDays() {
        val s = ChartMapper.scatter(listOf(day(0), day(1, focus = 30, warn = 2)), label)
        assertEquals(1, s.size)
        assertEquals(30f, s[0].x)
        assertEquals(2f, s[0].y)
    }

    @Test fun radarClampsAndHandlesEmptyMonth() {
        val names = List(5) { "x" }
        val empty = PeriodMetrics.monthly(emptyList(), YearMonth.of(2026, 9), LocalDate.of(2026, 8, 31), ZoneOffset.UTC)
        assertTrue(ChartMapper.radar(empty, names).all { it.fraction == 0f })
        val m = PeriodMetrics.monthly(emptyList(), YearMonth.of(2026, 9), d, ZoneOffset.UTC)
        val axes = ChartMapper.radar(m.copy(habitReduction = 3f), names)
        assertEquals(1f, axes[2].fraction)
        assertEquals("100%", axes[2].display)
    }

    @Test fun timelineFiltersDayAndSorts() {
        fun ev(t: HabitEventType, date: LocalDate, h: Int, m: Int = 0) =
            HabitEvent("e", t, null, null, date.atTime(h, m).atZone(ZoneOffset.UTC).toInstant().toEpochMilli())
        val items = ChartMapper.timeline(
            listOf(
                ev(HabitEventType.APP_BLOCKED, d, 22), ev(HabitEventType.FOCUS_STARTED, d, 9, 30),
                ev(HabitEventType.APP_OPENED, d, 10), ev(HabitEventType.WARNING_SHOWN, d.plusDays(1), 8),
            ),
            d, ZoneOffset.UTC,
        )
        assertEquals(listOf(TimelineKind.FOCUS_START, TimelineKind.BLOCKED), items.map { it.kind })
        assertEquals(570, items[0].minuteOfDay)
    }
}
