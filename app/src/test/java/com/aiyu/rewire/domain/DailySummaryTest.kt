package com.aiyu.rewire.domain

import com.aiyu.rewire.domain.analytics.DailySummary
import com.aiyu.rewire.domain.analytics.HabitEvent
import com.aiyu.rewire.domain.analytics.HabitEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class DailySummaryTest {
    private val today = LocalDate.of(2026, 10, 3)
    private fun ev(type: HabitEventType, date: LocalDate, meta: Map<String, String> = emptyMap()) =
        HabitEvent("e", type, null, null, date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), meta)

    @Test fun quietYesterdayIsSkipped() {
        assertNull(DailySummary.forYesterday(emptyList(), today, ZoneOffset.UTC))
        // Activity today or the day before yesterday does not count.
        val events = listOf(ev(HabitEventType.WARNING_SHOWN, today), ev(HabitEventType.WARNING_SHOWN, today.minusDays(2)))
        assertNull(DailySummary.forYesterday(events, today, ZoneOffset.UTC))
    }

    @Test fun summarisesYesterdayOnly() {
        val y = today.minusDays(1)
        val events = listOf(
            ev(HabitEventType.FOCUS_COMPLETED, y, mapOf(HabitEvent.KEY_FOCUS_MINUTES to "25")),
            ev(HabitEventType.WENT_BACK, y), ev(HabitEventType.WARNING_SHOWN, y),
            ev(HabitEventType.WARNING_SHOWN, today),
        )
        val m = DailySummary.forYesterday(events, today, ZoneOffset.UTC)!!
        assertEquals(y, m.date)
        assertEquals(25, m.focusMinutes)
        assertEquals(1, m.wentBackCount)
        assertEquals(1, m.warningCount)
    }
}
