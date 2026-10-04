package com.aiyu.rewire.domain

import com.aiyu.rewire.domain.quit.Milestone
import com.aiyu.rewire.domain.quit.Quit
import com.aiyu.rewire.domain.quit.Quit.DAY_MS
import com.aiyu.rewire.domain.quit.QuitData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuitTest {
    private val t0 = 1_000_000_000_000L

    private fun one(startedAt: Long = t0) = Quit.add(QuitData(), "a", "  Late nights ", " sleep ", startedAt, now = t0)

    @Test fun addTrimsAndRejectsBlankOrLongNames() {
        val d = one()
        assertEquals("Late nights", d.habits.single().name)
        assertEquals("sleep", d.habits.single().reason)
        assertEquals(QuitData(), Quit.add(QuitData(), "b", "  ", "", t0, t0))
        assertEquals(QuitData(), Quit.add(QuitData(), "b", "x".repeat(Quit.MAX_NAME + 1), "", t0, t0))
    }

    @Test fun futureStartIsClampedToNow() {
        assertEquals(t0, one(startedAt = t0 + DAY_MS).habits.single().startedAt)
    }

    @Test fun slipKeepsBestAndRestartsRun() {
        val now = t0 + 10 * DAY_MS
        val h = Quit.slip(one(), "a", now).habits.single()
        assertEquals(now, h.startedAt)
        assertEquals(10 * DAY_MS, h.bestMillis)
        assertEquals(listOf(now), h.slips)
        assertEquals(0, Quit.runDays(h, now))
        // A shorter run after the slip never lowers the best.
        val later = Quit.slip(Quit.slip(one(), "a", now), "a", now + DAY_MS).habits.single()
        assertEquals(10 * DAY_MS, later.bestMillis)
        assertEquals(2, later.slips.size)
    }

    @Test fun bestCountsTheRunningStreak() {
        val h = one().habits.single().copy(bestMillis = 2 * DAY_MS)
        assertEquals(5 * DAY_MS, Quit.bestMillis(h, t0 + 5 * DAY_MS))
        assertEquals(0, Quit.runMillis(h, t0 - DAY_MS)) // clock went back
    }

    @Test fun milestones() {
        assertEquals(Milestone(0, 1, 0f), Quit.milestone(0))
        assertEquals(Milestone(3, 7, 0.5f), Quit.milestone(5))
        assertEquals(Milestone(365, 730, 0f), Quit.milestone(365))
        assertEquals(730, Quit.milestone(400).next)
        assertEquals(listOf(1, 3, 7), Quit.reached(10))
    }

    @Test fun editAndDelete() {
        val d = Quit.edit(one(), "a", "Renamed", "", t0 - DAY_MS, t0)
        assertEquals("Renamed", d.habits.single().name)
        assertEquals(t0 - DAY_MS, d.habits.single().startedAt)
        assertEquals(one(), Quit.edit(one(), "a", "", "", t0, t0))
        assertTrue(Quit.delete(d, "a").habits.isEmpty())
    }

    @Test fun thoughtOfTheDayRotates() {
        assertEquals(3, Quit.thoughtIndex(3, 0, 10))
        assertEquals(0, Quit.thoughtIndex(9, 1, 10))
        assertEquals(9, Quit.thoughtIndex(0, -1, 10))
        assertEquals(0, Quit.thoughtIndex(5, 0, 0))
    }
}
