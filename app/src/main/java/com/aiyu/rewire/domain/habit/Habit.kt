package com.aiyu.rewire.domain.habit

import kotlinx.serialization.Serializable

enum class WarningLevel { MINOR, MAJOR, MAX }

@Serializable
data class Habit(
    val id: String,
    val name: String,
    val description: String?,
    val enabled: Boolean,
)

@Serializable
data class ProtectedApp(
    val packageName: String,
    val habitId: String,
    val warningLevel: WarningLevel,
    val enabled: Boolean,
    /** This app's own boundaries; null = it follows the habit's. */
    val limits: AppLimits? = null,
)

/**
 * Boundaries one app uses instead of its habit's. All of them replace the habit's together, so the rule
 * for an app is never a mix of two sources. Times are minutes from midnight; null = no boundary.
 */
@Serializable
data class AppLimits(
    val dailyLimitMinutes: Int? = null,
    val maxLaunches: Int? = null,
    val allowedStartMinutes: Int? = null,
    val allowedEndMinutes: Int? = null,
) {
    companion object {
        /** Starting point when an app gets its own limits: a copy of the habit's, so nothing changes until edited. */
        fun from(rule: RestrictionRule) = AppLimits(rule.dailyLimitMinutes, rule.maxLaunches, rule.allowedStartMinutes, rule.allowedEndMinutes)
    }
}

/** Times are minutes from midnight. Null = no boundary. */
@Serializable
data class RestrictionRule(
    val id: String,
    val habitId: String,
    val dailyLimitMinutes: Int?,
    val allowedStartMinutes: Int?,
    val allowedEndMinutes: Int?,
    val maxLaunches: Int?,
    val warningLevel: WarningLevel,
    /** Seconds the Major warning holds "Continue" disabled. */
    val pauseSeconds: Int,
    /** Smart escalation (CLAUDE.md §4): off by default; when on, usage minutes pick the effective level. */
    val escalationEnabled: Boolean = false,
    /** Usage minutes at which the level rises to Major (below it: Minor). */
    val escalationMajorAfterMinutes: Int = DEFAULT_ESCALATION_MAJOR_MINUTES,
    /** Usage minutes at which the level rises to Max. Must exceed the Major threshold. */
    val escalationMaxAfterMinutes: Int = DEFAULT_ESCALATION_MAX_MINUTES,
) {
    /** Tiers only apply when switched on and the thresholds are sane; a corrupt row falls back to the plain level. */
    val escalation get() = escalationEnabled && isValidEscalation(escalationMajorAfterMinutes, escalationMaxAfterMinutes)

    /** Level for [usageMinutes]; the configured level when escalation is off or usage is unknown. */
    fun effectiveLevel(usageMinutes: Int?): WarningLevel = when {
        !escalation || usageMinutes == null -> warningLevel
        usageMinutes >= escalationMaxAfterMinutes -> WarningLevel.MAX
        usageMinutes >= escalationMajorAfterMinutes -> WarningLevel.MAJOR
        else -> WarningLevel.MINOR
    }

    companion object {
        const val DEFAULT_ESCALATION_MAJOR_MINUTES = 20
        const val DEFAULT_ESCALATION_MAX_MINUTES = 40
        const val MAX_ESCALATION_MINUTES = 24 * 60

        /** Three fixed tiers Minor/Major/Max: thresholds strictly increasing, within a day. */
        /** Applies an edit to one threshold and nudges the other so the pair stays valid (editing never fails). */
        fun withMajor(rule: RestrictionRule, major: Int): RestrictionRule {
            val m = major.coerceIn(1, MAX_ESCALATION_MINUTES - 1)
            return rule.copy(escalationMajorAfterMinutes = m, escalationMaxAfterMinutes = maxOf(rule.escalationMaxAfterMinutes, m + 1))
        }

        fun withMax(rule: RestrictionRule, max: Int): RestrictionRule {
            val x = max.coerceIn(2, MAX_ESCALATION_MINUTES)
            return rule.copy(escalationMaxAfterMinutes = x, escalationMajorAfterMinutes = minOf(rule.escalationMajorAfterMinutes, x - 1))
        }

        fun isValidEscalation(major: Int, max: Int) = major >= 1 && major < max && max <= MAX_ESCALATION_MINUTES
    }
}

/** Aggregate the UI works with: one habit + its apps + its rule. */
@Serializable
data class HabitProfile(
    val habit: Habit,
    val apps: List<ProtectedApp>,
    val rule: RestrictionRule,
) {
    val id get() = habit.id
    val level get() = rule.warningLevel

    /** The rule [pkg] is judged by: the habit's, with the app's own boundaries swapped in when it has them. */
    fun ruleFor(pkg: String): RestrictionRule {
        val l = apps.firstOrNull { it.packageName == pkg }?.limits ?: return rule
        return rule.copy(
            dailyLimitMinutes = l.dailyLimitMinutes,
            maxLaunches = l.maxLaunches,
            allowedStartMinutes = l.allowedStartMinutes,
            allowedEndMinutes = l.allowedEndMinutes,
        )
    }

    /** This profile as seen from [pkg]: same habit, level and escalation; boundaries resolved for that app. */
    fun forApp(pkg: String) = copy(rule = ruleFor(pkg))
}
