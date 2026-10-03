package com.aiyu.rewire.domain

import com.aiyu.rewire.domain.analytics.DailyMetrics
import com.aiyu.rewire.domain.analytics.HabitEvent
import com.aiyu.rewire.domain.analytics.HabitEventType
import com.aiyu.rewire.domain.analytics.PeriodMetrics
import com.aiyu.rewire.domain.goals.GoalRules
import com.aiyu.rewire.domain.goals.Goals
import com.aiyu.rewire.domain.goals.Streaks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

class GoalsTest {
    private val d0 = LocalDate.of(2026, 9, 1)

    private fun day(i: Int, focus: Int = 0, overrides: Int = 0, warnings: Int = 0) = DailyMetrics(
        d0.plusDays(i.toLong()), focus, 0, 0, warnings, overrides, 0, 0,
    )

    /** Days 0..n-1, "good" = 30 focus minutes. */
    private fun run(vararg good: Boolean) = good.mapIndexed { i, g -> day(i, focus = if (g) 30 else 0) }

    @Test fun parseBlankIsUnsetAndBadInputIsNull() {
        assertEquals(Goals(), GoalRules.parse("", " "))
        assertEquals(Goals(45, 0), GoalRules.parse("45", "0"))
        assertNull(GoalRules.parse("4", ""))
        assertNull(GoalRules.parse("721", ""))
        assertNull(GoalRules.parse("abc", ""))
        assertNull(GoalRules.parse("", "-1"))
        assertNull(GoalRules.parse("", "51"))
    }

    @Test fun validity() {
        assertTrue(Goals().isValid)
        assertFalse(Goals(dailyFocusMinutes = 0).isValid)
        assertFalse(Goals(maxOverridesPerDay = -1).isValid)
    }

    @Test fun goalMetNeedsEverySetGoalAndActivity() {
        val g = Goals(60, 1)
        assertTrue(GoalRules.met(day(0, focus = 60, overrides = 1), g))
        assertFalse(GoalRules.met(day(0, focus = 59), g))
        assertFalse(GoalRules.met(day(0, focus = 60, overrides = 2), g))
        assertFalse(GoalRules.met(day(0), Goals(maxOverridesPerDay = 0))) // empty day is not a free pass
        assertTrue(GoalRules.met(day(0, warnings = 1), Goals(maxOverridesPerDay = 0)))
    }

    @Test fun statusFractionClamps() {
        assertEquals(0.5f, GoalRules.status(day(0, focus = 30), Goals(60)).focusFraction!!, 0f)
        assertEquals(1f, GoalRules.status(day(0, focus = 90), Goals(60)).focusFraction!!, 0f)
        assertNull(GoalRules.status(day(0, focus = 90), Goals()).focusFraction)
    }

    @Test fun streakWithGapAndBest() {
        val s = Streaks.compute(run(true, true, true, false, true, true), Goals())
        assertEquals(2, s.current)
        assertEquals(3, s.best)
        assertTrue(s.todayMet)
    }

    @Test fun todayInProgressDoesNotBreakStreak() {
        val s = Streaks.compute(run(true, true, true, false), Goals())
        assertEquals(3, s.current)
        assertFalse(s.todayMet)
    }

    @Test fun yesterdayMissedBreaksIt() {
        assertEquals(0, Streaks.compute(run(true, true, false, false), Goals()).current)
    }

    @Test fun emptyWindow() {
        assertEquals(0, Streaks.compute(emptyList(), Goals()).best)
    }

    @Test fun streakUsesGoalsWhenSet() {
        val days = listOf(day(0, focus = 30), day(1, focus = 60), day(2, focus = 60))
        assertEquals(3, Streaks.compute(days, Goals()).current)
        assertEquals(2, Streaks.compute(days, Goals(dailyFocusMinutes = 60)).current)
    }

    @Test fun defaultRuleCountsHandledFriction() {
        assertTrue(PeriodMetrics.dayOnTrack(day(0, warnings = 2, overrides = 1))) // discipline 0.5
        assertFalse(PeriodMetrics.dayOnTrack(day(0, warnings = 2, overrides = 2)))
        assertFalse(PeriodMetrics.dayOnTrack(day(0)))
    }

    private fun ev(zone: ZoneId, date: LocalDate, hour: Int, min: Int = 0) = HabitEvent(
        "e$date$hour", HabitEventType.FOCUS_COMPLETED, null, null,
        date.atTime(hour, min).atZone(zone).toInstant().toEpochMilli(), mapOf(HabitEvent.KEY_FOCUS_MINUTES to "25"),
    )

    @Test fun dayBoundaryFollowsTheZone() {
        val today = LocalDate.of(2026, 10, 3)
        val tokyo = ZoneId.of("Asia/Tokyo")
        // 23:30 JST Oct 1 and 00:15 JST Oct 2: two different local days in Tokyo...
        val events = listOf(ev(tokyo, today.minusDays(2), 23, 30), ev(tokyo, today.minusDays(1), 0, 15))
        assertEquals(2, Streaks.progress(events, today, tokyo, Goals()).streak.current)
        // ...but both fall on Oct 1 in UTC (14:30 and 15:15), so Oct 2 is empty and the streak is 0.
        val utc = Streaks.progress(events, today, ZoneOffset.UTC, Goals())
        assertEquals(0, utc.streak.current)
        assertEquals(1, utc.streak.best)
    }

    @Test fun monthlyGoalCompletionKeepsOldDefaultAndUsesGoals() {
        val z = ZoneOffset.UTC
        val today = LocalDate.of(2026, 9, 4)
        val events = listOf(ev(z, LocalDate.of(2026, 9, 1), 10), ev(z, LocalDate.of(2026, 9, 2), 10))
        val ym = YearMonth.of(2026, 9)
        assertEquals(0.5f, PeriodMetrics.monthly(events, ym, today, z).goalCompletion, 0f)
        assertEquals(0f, PeriodMetrics.monthly(events, ym, today, z, goals = Goals(dailyFocusMinutes = 30)).goalCompletion, 0f)
        assertEquals(0.5f, PeriodMetrics.monthly(events, ym, today, z, goals = Goals(dailyFocusMinutes = 20)).goalCompletion, 0f)
    }
}
