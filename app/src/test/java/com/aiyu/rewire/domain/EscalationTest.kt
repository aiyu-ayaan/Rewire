package com.aiyu.rewire.domain

import com.aiyu.rewire.domain.habit.Habit
import com.aiyu.rewire.domain.habit.HabitProfile
import com.aiyu.rewire.domain.habit.RestrictionRule
import com.aiyu.rewire.domain.habit.WarningLevel
import com.aiyu.rewire.domain.restriction.BlockReason
import com.aiyu.rewire.domain.restriction.RestrictionDecision.Allow
import com.aiyu.rewire.domain.restriction.RestrictionDecision.Block
import com.aiyu.rewire.domain.restriction.RestrictionDecision.Warn
import com.aiyu.rewire.domain.restriction.RuleEngine
import com.aiyu.rewire.domain.restriction.RuleInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EscalationTest {
    private fun input(
        usage: Int?, on: Boolean = true, base: WarningLevel = WarningLevel.MINOR,
        major: Int = 20, max: Int = 40, daily: Int? = null,
        focusing: Boolean = false, bMinor: Boolean = false, bMajor: Boolean = false, bMax: Boolean = false,
    ) = RuleInput(
        HabitProfile(Habit("h", "H", null, true), emptyList(), RestrictionRule("r", "h", daily, null, null, null, base, 5, on, major, max)),
        nowMinutes = 12 * 60, launchesToday = 0, usageMinutesToday = usage,
        focusing = focusing, bypassMinor = bMinor, bypassMajor = bMajor, bypassMax = bMax,
    )

    @Test fun tiersFollowUsage() {
        assertEquals(Warn(WarningLevel.MINOR), RuleEngine.decide(input(0)))
        assertEquals(Warn(WarningLevel.MINOR), RuleEngine.decide(input(19)))
        assertEquals(Warn(WarningLevel.MAJOR), RuleEngine.decide(input(20)))
        assertEquals(Warn(WarningLevel.MAJOR), RuleEngine.decide(input(39)))
        assertEquals(Block(BlockReason.ESCALATION), RuleEngine.decide(input(40)))
        assertEquals(Block(BlockReason.ESCALATION), RuleEngine.decide(input(500)))
    }

    @Test fun tiersOverrideConfiguredLevel() {
        assertEquals(Warn(WarningLevel.MINOR), RuleEngine.decide(input(5, base = WarningLevel.MAX)))
    }

    @Test fun offByDefaultBehavesAsBefore() {
        val rule = RestrictionRule("r", "h", null, null, null, null, WarningLevel.MAJOR, 5)
        assertFalse(rule.escalationEnabled)
        assertEquals(Warn(WarningLevel.MAJOR), RuleEngine.decide(input(100, on = false, base = WarningLevel.MAJOR)))
        assertNull(RuleEngine.minutesUntilNextBoundary(input(5, on = false)))
    }

    @Test fun unknownUsageFallsBackToConfiguredLevel() {
        assertEquals(Warn(WarningLevel.MAJOR), RuleEngine.decide(input(null, base = WarningLevel.MAJOR)))
        assertEquals(Block(BlockReason.ALWAYS), RuleEngine.decide(input(null, base = WarningLevel.MAX)))
    }

    @Test fun invalidThresholdsAreIgnored() {
        assertEquals(Warn(WarningLevel.MAJOR), RuleEngine.decide(input(100, base = WarningLevel.MAJOR, major = 40, max = 40)))
    }

    @Test fun boundaryIsNextTierAhead() {
        assertEquals(15, RuleEngine.minutesUntilNextBoundary(input(5)))
        assertEquals(20, RuleEngine.minutesUntilNextBoundary(input(20)))
        assertEquals(1, RuleEngine.minutesUntilNextBoundary(input(39)))
        assertNull(RuleEngine.minutesUntilNextBoundary(input(40)))
        assertNull(RuleEngine.minutesUntilNextBoundary(input(null)))
    }

    @Test fun boundaryTakesNearestOfLimitAndTier() {
        assertEquals(3, RuleEngine.minutesUntilNextBoundary(input(5, daily = 8)))
    }

    @Test fun bypassUsesEffectiveLevel() {
        assertEquals(Allow, RuleEngine.decide(input(5, focusing = true, bMinor = true)))
        assertEquals(Warn(WarningLevel.MAJOR), RuleEngine.decide(input(25, focusing = true, bMinor = true)))
        assertEquals(Allow, RuleEngine.decide(input(25, focusing = true, bMajor = true)))
        assertEquals(Block(BlockReason.ESCALATION), RuleEngine.decide(input(45, focusing = true, bMinor = true, bMajor = true)))
        assertEquals(Allow, RuleEngine.decide(input(45, focusing = true, bMax = true)))
    }

    @Test fun validation() {
        assertTrue(RestrictionRule.isValidEscalation(20, 40))
        assertFalse(RestrictionRule.isValidEscalation(40, 20))
        assertFalse(RestrictionRule.isValidEscalation(20, 20))
        assertFalse(RestrictionRule.isValidEscalation(0, 20))
        assertFalse(RestrictionRule.isValidEscalation(20, RestrictionRule.MAX_ESCALATION_MINUTES + 1))
    }

    @Test fun editingKeepsThresholdsValid() {
        val r = RestrictionRule("r", "h", null, null, null, null, WarningLevel.MINOR, 5)
        val a = RestrictionRule.withMajor(r, 60) // pushes Max above it
        assertTrue(RestrictionRule.isValidEscalation(a.escalationMajorAfterMinutes, a.escalationMaxAfterMinutes))
        val b = RestrictionRule.withMax(r, 10) // pulls Major below it
        assertTrue(RestrictionRule.isValidEscalation(b.escalationMajorAfterMinutes, b.escalationMaxAfterMinutes))
        assertEquals(10, b.escalationMaxAfterMinutes)
    }
}
