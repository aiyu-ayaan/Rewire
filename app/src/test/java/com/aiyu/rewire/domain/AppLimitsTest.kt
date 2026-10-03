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
    private fun app(pkg: String, limits: AppLimits = AppLimits()) = ProtectedApp(pkg, "h", WarningLevel.MAX, true, limits)

    @Test fun appWithoutOverridesFollowsTheHabit() =
        assertEquals(habitRule, profile(app("fb")).ruleFor("fb"))

    @Test fun overridingTheDailyLimitKeepsTheGlobalLaunchLimitAndWindow() {
        val r = profile(app("fb", AppLimits(ownDailyLimit = true, dailyLimitMinutes = 10))).ruleFor("fb")
        assertEquals(10, r.dailyLimitMinutes)
        assertEquals(5, r.maxLaunches)
        assertEquals(540, r.allowedStartMinutes)
        assertEquals(600, r.allowedEndMinutes)
    }

    @Test fun overridingTheWindowAndLaunchesKeepsTheGlobalDailyLimit() {
        val r = profile(app("fb", AppLimits(ownLaunchLimit = true, maxLaunches = 2, ownWindow = true, allowedStartMinutes = 1200, allowedEndMinutes = 1260))).ruleFor("fb")
        assertEquals(60, r.dailyLimitMinutes)
        assertEquals(2, r.maxLaunches)
        assertEquals(1200, r.allowedStartMinutes)
        assertEquals(1260, r.allowedEndMinutes)
    }

    @Test fun anOverrideCanRemoveALimitForOneApp() {
        val r = profile(app("fb", AppLimits(ownDailyLimit = true, dailyLimitMinutes = null, ownWindow = true))).ruleFor("fb")
        assertNull(r.dailyLimitMinutes)
        assertNull(r.allowedStartMinutes)
        assertEquals(5, r.maxLaunches)
    }

    @Test fun overridesNeverTouchLevelEscalationOrOtherApps() {
        val p = profile(app("fb", AppLimits(ownDailyLimit = true, dailyLimitMinutes = 10)), app("ig"))
        assertEquals(WarningLevel.MAX, p.ruleFor("fb").warningLevel)
        assertEquals(true, p.ruleFor("fb").escalationEnabled)
        assertEquals(habitRule, p.ruleFor("ig"))
        assertEquals(habitRule, p.ruleFor("not-in-habit"))
    }
}
