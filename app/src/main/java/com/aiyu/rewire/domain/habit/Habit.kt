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
)

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
