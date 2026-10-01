package com.rewire.app.domain

import com.rewire.app.domain.habit.Habit
import com.rewire.app.domain.habit.HabitProfile
import com.rewire.app.domain.habit.RestrictionRule
import com.rewire.app.domain.habit.WarningLevel
import com.rewire.app.domain.restriction.BlockReason
import com.rewire.app.domain.restriction.RestrictionDecision.Allow
import com.rewire.app.domain.restriction.RestrictionDecision.Block
import com.rewire.app.domain.restriction.RestrictionDecision.Warn
import com.rewire.app.domain.restriction.RuleEngine
import com.rewire.app.domain.restriction.RuleInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {
    private fun input(
        level: WarningLevel, enabled: Boolean = true,
        daily: Int? = null, start: Int? = null, end: Int? = null, launches: Int? = null,
        now: Int = 12 * 60, launchesToday: Int = 0, usage: Int? = null,
        focusing: Boolean = false, bMinor: Boolean = true, bMajor: Boolean = false, bMax: Boolean = false,
    ) = RuleInput(
        HabitProfile(Habit("h", "H", null, enabled), emptyList(), RestrictionRule("r", "h", daily, start, end, launches, level, 5)),
        now, launchesToday, usage, focusing, bMinor, bMajor, bMax,
    )

    @Test fun minorWarns() = assertEquals(Warn(WarningLevel.MINOR), RuleEngine.decide(input(WarningLevel.MINOR)))
    @Test fun majorWarns() = assertEquals(Warn(WarningLevel.MAJOR), RuleEngine.decide(input(WarningLevel.MAJOR)))
    @Test fun disabledHabitAllows() = assertEquals(Allow, RuleEngine.decide(input(WarningLevel.MAX, enabled = false)))

    @Test fun maxWithoutBoundariesAlwaysBlocks() =
        assertEquals(Block(BlockReason.ALWAYS), RuleEngine.decide(input(WarningLevel.MAX)))

    @Test fun launchLimitAllowsUpToLimitThenBlocks() {
        assertEquals(Allow, RuleEngine.decide(input(WarningLevel.MAX, launches = 1, launchesToday = 0)))
        assertEquals(Block(BlockReason.LAUNCH_LIMIT), RuleEngine.decide(input(WarningLevel.MAX, launches = 1, launchesToday = 1)))
    }

    @Test fun allowedWindow() {
        assertEquals(Allow, RuleEngine.decide(input(WarningLevel.MAX, start = 540, end = 570, now = 545)))
        assertEquals(Block(BlockReason.OUTSIDE_WINDOW), RuleEngine.decide(input(WarningLevel.MAX, start = 540, end = 570, now = 600)))
    }

    @Test fun overnightWindow() {
        assertTrue(RuleEngine.inWindow(23 * 60, 22 * 60, 6 * 60))
        assertTrue(RuleEngine.inWindow(60, 22 * 60, 6 * 60))
        assertFalse(RuleEngine.inWindow(12 * 60, 22 * 60, 6 * 60))
    }

    @Test fun dailyLimitBlocksOnlyWhenUsageKnown() {
        assertEquals(Block(BlockReason.DAILY_LIMIT), RuleEngine.decide(input(WarningLevel.MAX, daily = 30, usage = 30)))
        assertEquals(Allow, RuleEngine.decide(input(WarningLevel.MAX, daily = 30, usage = 10)))
        assertEquals(Allow, RuleEngine.decide(input(WarningLevel.MAX, daily = 30, usage = null)))
    }

    @Test fun focusBypassFollowsSettings() {
        assertEquals(Allow, RuleEngine.decide(input(WarningLevel.MINOR, focusing = true)))
        assertEquals(Warn(WarningLevel.MAJOR), RuleEngine.decide(input(WarningLevel.MAJOR, focusing = true)))
        assertEquals(Block(BlockReason.ALWAYS), RuleEngine.decide(input(WarningLevel.MAX, focusing = true)))
        assertEquals(Allow, RuleEngine.decide(input(WarningLevel.MAX, focusing = true, bMax = true)))
    }


    @Test fun nextBoundaryIsEarliestOfWindowEndAndLimit() {
        assertEquals(20, RuleEngine.minutesUntilNextBoundary(input(WarningLevel.MAX, start = 540, end = 600, now = 580, daily = 60, usage = 30)))
        assertEquals(5, RuleEngine.minutesUntilNextBoundary(input(WarningLevel.MAX, daily = 60, usage = 55)))
        assertNull(RuleEngine.minutesUntilNextBoundary(input(WarningLevel.MAJOR, daily = 60, usage = 55)))
    }
}
