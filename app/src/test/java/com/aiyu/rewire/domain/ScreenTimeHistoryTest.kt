package com.aiyu.rewire.domain

import com.aiyu.rewire.domain.analytics.ScreenTimeHistory
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ScreenTimeHistoryTest {
    private val today = LocalDate.of(2026, 10, 4)

    @Test fun mergeNeverLowersAStoredDay() {
        val m = ScreenTimeHistory.merge(mapOf(today to 90), today, 40, today)
        assertEquals(90, m[today])
        assertEquals(120, ScreenTimeHistory.merge(m, today, 120, today)[today])
    }

    @Test fun mergeDropsDaysOlderThanTheKeepWindow() {
        val old = today.minusDays(ScreenTimeHistory.KEEP_DAYS + 1)
        val m = ScreenTimeHistory.merge(mapOf(old to 10, today.minusDays(5) to 20), today, 30, today)
        assertEquals(setOf(today.minusDays(5), today), m.keys)
    }

    @Test fun syncsTodayAlwaysAndPastDaysOnlyWhenMissing() {
        val stored = mapOf(today.minusDays(1) to 50, today to 10)
        val days = ScreenTimeHistory.daysToSync(stored, today)
        assertEquals(today, days.last())
        assertEquals(false, today.minusDays(1) in days)
        assertEquals(6, days.size) // 5 missing past days + today
    }
}
