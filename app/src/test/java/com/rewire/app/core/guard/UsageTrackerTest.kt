package com.rewire.app.core.guard

import com.rewire.app.core.guard.UsageTracker.Event
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageTrackerTest {
    private val min = 60_000L
    private val ig = "com.instagram.android"

    @Test
    fun countsOnlyResumedTimeToday() {
        val events = listOf(
            Event(ig, "Main", 1, 10 * min),
            Event(ig, "Main", 2, 13 * min),
            Event(ig, "Main", 23, 13 * min), // stop after pause must not double count
            Event(ig, "Main", 1, 20 * min),
            Event(ig, "Main", 2, 22 * min),
        )
        assertEquals(5 * min, UsageTracker.foregroundMillis(events, 0L, 60 * min)[ig])
    }

    @Test
    fun appOpenAtMidnightCountsFromStart() {
        val events = listOf(Event(ig, "Main", 2, 3 * min))
        assertEquals(3 * min, UsageTracker.foregroundMillis(events, 0L, 60 * min)[ig])
    }

    @Test
    fun activitySwitchInsideAppIsOneSession() {
        val events = listOf(
            Event(ig, "A", 1, 0L),
            Event(ig, "B", 1, 1 * min),
            Event(ig, "A", 2, 1 * min),
            Event(ig, "B", 2, 4 * min),
        )
        assertEquals(4 * min, UsageTracker.foregroundMillis(events, 0L, 60 * min)[ig])
    }

    @Test
    fun openSessionRunsToNowAndScreenOffCloses() {
        val events = listOf(
            Event(ig, "Main", 1, 0L),
            Event(null, null, 16, 2 * min),
            Event(ig, "Main", 1, 50 * min),
        )
        assertEquals(12 * min, UsageTracker.foregroundMillis(events, 0L, 60 * min)[ig])
    }

    @Test
    fun resumesKeepsEveryResumeInOrderAndNoPauses() {
        val events = listOf(
            Event(ig, "Main", 1, 1_000L),
            Event("com.android.systemui", "Shade", 1, 1_200L), // must not hide the protected open before it
            Event(ig, "Main", 2, 2_100L),
            Event(null, null, 1, 2_200L),
        )
        assertEquals(listOf(ig, "com.android.systemui"), UsageTracker.resumes(events).map { it.pkg })
        assertEquals(emptyList<Event>(), UsageTracker.resumes(listOf(Event(ig, "Main", 2, 1L))))
    }
}
