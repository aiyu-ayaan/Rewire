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
)

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
}
