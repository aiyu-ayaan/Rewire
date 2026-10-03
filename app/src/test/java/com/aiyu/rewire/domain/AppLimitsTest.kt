package com.aiyu.rewire.domain

import com.aiyu.rewire.domain.habit.AppLimits
import com.aiyu.rewire.domain.habit.Habit
import com.aiyu.rewire.domain.habit.HabitProfile
import com.aiyu.rewire.domain.habit.ProtectedApp
import com.aiyu.rewire.domain.habit.RestrictionRule
import com.aiyu.rewire.domain.habit.WarningLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLimitsTest {
    private val habitRule = RestrictionRule("r", "h", dailyLimitMinutes = 60, allowedStartMinutes = 540, allowedEndMinutes = 600, maxLaunches = 5, WarningLevel.MAX, 5, escalationEnabled = true)

    private fun profile(vararg apps: ProtectedApp) = HabitProfile(Habit("h", "Social", null, true), apps.toList(), habitRule)

    @Test fun appWithoutOwnLimitsFollowsTheHabit() {
        val p = profile(ProtectedApp("fb", "h", WarningLevel.MAX, true))
        assertEquals(habitRule, p.ruleFor("fb"))
    }

    @Test fun ownLimitsReplaceEveryBoundaryButKeepLevelAndEscalation() {
        val p = profile(ProtectedApp("fb", "h", WarningLevel.MAX, true, AppLimits(dailyLimitMinutes = 10, maxLaunches = null, allowedStartMinutes = 1200, allowedEndMinutes = 1260)))
        val r = p.ruleFor("fb")
        assertEquals(10, r.dailyLimitMinutes)
        assertNull(r.maxLaunches) // own "no launch limit", not the habit's 5
        assertEquals(1200, r.allowedStartMinutes)
        assertEquals(1260, r.allowedEndMinutes)
        assertEquals(WarningLevel.MAX, r.warningLevel)
        assertEquals(true, r.escalationEnabled)
    }

    @Test fun otherAppsKeepTheHabitRule() {
        val p = profile(
            ProtectedApp("fb", "h", WarningLevel.MAX, true, AppLimits(dailyLimitMinutes = 10)),
            ProtectedApp("ig", "h", WarningLevel.MAX, true),
        )
        assertEquals(habitRule, p.ruleFor("ig"))
        assertEquals(habitRule, p.ruleFor("not-in-habit"))
    }

    @Test fun startingOwnLimitsCopiesTheHabitSoNothingChangesYet() {
        val p = profile(ProtectedApp("fb", "h", WarningLevel.MAX, true, AppLimits.from(habitRule)))
        assertEquals(habitRule, p.ruleFor("fb"))
    }
}
