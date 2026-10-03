package com.aiyu.rewire.domain

import com.aiyu.rewire.domain.analytics.HabitEvent
import com.aiyu.rewire.domain.analytics.HabitEventType
import com.aiyu.rewire.domain.analytics.HabitEventType.*
import com.aiyu.rewire.domain.analytics.PeriodMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

class PeriodMetricsTest {
    private val utc = ZoneOffset.UTC
    private val mon = LocalDate.of(2026, 9, 28) // a Monday

    private fun ev(type: HabitEventType, date: LocalDate, hour: Int = 12, pkg: String? = null, focus: Int? = null) =
        HabitEvent(
            "e", type, pkg, null, date.atTime(hour, 0).atZone(utc).toInstant().toEpochMilli(),
            if (focus != null) mapOf(HabitEvent.KEY_FOCUS_MINUTES to focus.toString()) else emptyMap(),
        )

    @Test fun weekStartSnapsToMonday() {
        assertEquals(mon, PeriodMetrics.weekStart(LocalDate.of(2026, 10, 4)))
        assertEquals(mon, PeriodMetrics.weekStart(mon))
        assertEquals(LocalDate.of(2026, 10, 4), PeriodMetrics.weekStart(LocalDate.of(2026, 10, 4), DayOfWeek.SUNDAY))
    }

    @Test fun emptyWeekIsZeroedSevenDays() {
        val w = PeriodMetrics.weekly(emptyList(), mon, utc)
        assertEquals(7, w.days.size)
        assertEquals(0, w.focusMinutes)
        assertNull(w.mostOpenedApp)
        assertNull(w.mostProblematicHour)
        assertEquals(0, w.longestFocusMinutes)
    }

    @Test fun weeklyAggregates() {
        val events = listOf(
            ev(FOCUS_COMPLETED, mon, focus = 50),
            ev(FOCUS_COMPLETED, mon.plusDays(1), focus = 20),
            ev(APP_OPENED, mon, pkg = "a"), ev(APP_OPENED, mon.plusDays(1), pkg = "b"), ev(APP_OPENED, mon.plusDays(2), pkg = "b"),
            ev(WARNING_SHOWN, mon, hour = 22), ev(WARNING_SHOWN, mon.plusDays(1), hour = 22), ev(APP_BLOCKED, mon.plusDays(2), hour = 9),
            ev(WENT_BACK, mon), ev(OVERRIDE_USED, mon.plusDays(2)),
        )
        val w = PeriodMetrics.weekly(events, mon, utc, mapOf(mon to 90, mon.plusDays(3) to 30))
        assertEquals(70, w.focusMinutes)
        assertEquals(50, w.longestFocusMinutes)
        assertEquals(120, w.distractedMinutes)
        assertEquals(2f, w.distractedHours, 0f)
        assertEquals(3, w.habitAttempts)
        assertEquals(1, w.successfulBlocks) // went back; the one block was overridden
        assertEquals("b", w.mostOpenedApp?.packageName)
        assertEquals(2, w.mostOpenedApp?.opens)
        assertEquals(22, w.mostProblematicHour)
    }

    @Test fun weekBoundariesAreHalfOpen() {
        val events = listOf(
            ev(WARNING_SHOWN, mon.minusDays(1), hour = 23),
            ev(WARNING_SHOWN, mon, hour = 0),
            ev(WARNING_SHOWN, mon.plusDays(6), hour = 23),
            ev(WARNING_SHOWN, mon.plusDays(7), hour = 0),
        )
        assertEquals(2, PeriodMetrics.weekly(events, mon, utc).habitAttempts)
    }

    @Test fun weeklyRespectsTimezone() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        // Monday 00:30 in Tokyo is still Sunday in UTC.
        val e = HabitEvent("x", WARNING_SHOWN, null, null, mon.atTime(0, 30).atZone(tokyo).toInstant().toEpochMilli())
        assertEquals(1, PeriodMetrics.weekly(listOf(e), mon, tokyo).habitAttempts)
        assertEquals(0, PeriodMetrics.weekly(listOf(e), mon, utc).habitAttempts)
        assertEquals(0, PeriodMetrics.weekly(listOf(e), mon, tokyo).mostProblematicHour)
    }

    @Test fun monthLengthsAndTodayCap() {
        val far = LocalDate.of(2030, 1, 1)
        assertEquals(28, PeriodMetrics.monthly(emptyList(), YearMonth.of(2026, 2), far, utc).days.size)
        assertEquals(29, PeriodMetrics.monthly(emptyList(), YearMonth.of(2028, 2), far, utc).days.size)
        assertEquals(31, PeriodMetrics.monthly(emptyList(), YearMonth.of(2026, 10), far, utc).days.size)
        assertEquals(3, PeriodMetrics.monthly(emptyList(), YearMonth.of(2026, 10), LocalDate.of(2026, 10, 3), utc).days.size)
        assertEquals(0, PeriodMetrics.monthly(emptyList(), YearMonth.of(2026, 11), LocalDate.of(2026, 10, 3), utc).days.size)
    }

    @Test fun emptyMonthHasNoRatiosToDivide() {
        val m = PeriodMetrics.monthly(emptyList(), YearMonth.of(2026, 11), LocalDate.of(2026, 10, 3), utc)
        assertEquals(0f, m.consistency, 0f)
        assertEquals(0f, m.goalCompletion, 0f)
        assertNull(m.habitReduction)
    }

    @Test fun singleDayMonth() {
        val d1 = LocalDate.of(2026, 10, 1)
        val m = PeriodMetrics.monthly(listOf(ev(FOCUS_COMPLETED, d1, focus = 25)), YearMonth.of(2026, 10), d1, utc, mapOf(d1 to 40))
        assertEquals(1, m.days.size)
        assertEquals(25, m.focusTrend.single().value)
        assertEquals(40, m.screenTimeTrend.single().value)
        assertEquals(1f, m.goalCompletion, 0f)
        assertEquals(1f, m.consistency, 0f)
        assertNull(m.habitReduction)
    }

    @Test fun monthlyTrendsReductionAndConsistency() {
        val d = { n: Int -> LocalDate.of(2026, 10, n) }
        val events = listOf(
            ev(WARNING_SHOWN, d(1)), ev(WARNING_SHOWN, d(1)), ev(WARNING_SHOWN, d(2)), ev(WARNING_SHOWN, d(2)), // 4 early
            ev(WARNING_SHOWN, d(3)), // 1 late
            ev(OVERRIDE_USED, d(4)),
        )
        val m = PeriodMetrics.monthly(events, YearMonth.of(2026, 10), d(4), utc)
        assertEquals(0.75f, m.habitReduction!!, 0.0001f)
        assertEquals(listOf(0, 0, 0, 1), m.overrideTrend.map { it.value })
        // d1-d3 handled without overrides => on track; d4 has no friction => not
        assertEquals(0.75f, m.consistency, 0.0001f)
    }
}
