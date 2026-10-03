package com.aiyu.rewire.domain.goals

import com.aiyu.rewire.domain.analytics.DailyMetrics
import kotlinx.serialization.Serializable

/** Optional daily targets. Null = not set; both null means "no goals". */
@Serializable
data class Goals(
    val dailyFocusMinutes: Int? = null,
    val maxOverridesPerDay: Int? = null,
) {
    val isEmpty get() = dailyFocusMinutes == null && maxOverridesPerDay == null
    val isValid get() = (dailyFocusMinutes == null || dailyFocusMinutes in FOCUS_RANGE) &&
        (maxOverridesPerDay == null || maxOverridesPerDay in OVERRIDE_RANGE)

    companion object {
        val FOCUS_RANGE = 5..720
        val OVERRIDE_RANGE = 0..50
    }
}

/** Progress of one day against [Goals]; fractions are 0..1 and null when that goal is unset. */
data class GoalStatus(
    val focusMinutes: Int,
    val focusTarget: Int?,
    val overrides: Int,
    val overridesMax: Int?,
) {
    val focusMet get() = focusTarget == null || focusMinutes >= focusTarget
    val overridesOk get() = overridesMax == null || overrides <= overridesMax
    val focusFraction: Float? get() = focusTarget?.let { (focusMinutes.toFloat() / it).coerceIn(0f, 1f) }
}

object GoalRules {
    /** Blank = unset. Returns null when either field is not a whole number in range (so a form can disable Save). */
    fun parse(focus: String, overrides: String): Goals? {
        val f = if (focus.isBlank()) null else focus.trim().toIntOrNull()?.takeIf { it in Goals.FOCUS_RANGE } ?: return null
        val o = if (overrides.isBlank()) null else overrides.trim().toIntOrNull()?.takeIf { it in Goals.OVERRIDE_RANGE } ?: return null
        return Goals(f, o)
    }

    fun status(day: DailyMetrics, goals: Goals) =
        GoalStatus(day.focusMinutes, goals.dailyFocusMinutes, day.overrideCount, goals.maxOverridesPerDay)

    /**
     * Day counts when every set goal holds and the day shows some activity (so an empty day never
     * passes an "at most N overrides" goal for free).
     */
    fun met(day: DailyMetrics, goals: Goals): Boolean {
        val s = status(day, goals)
        val active = day.focusMinutes > 0 || day.frictionMoments > 0 || day.appOpens > 0
        return active && s.focusMet && s.overridesOk
    }
}
