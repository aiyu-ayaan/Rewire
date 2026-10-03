package com.aiyu.rewire.data.local

import com.aiyu.rewire.core.settings.FocusBypass
import com.aiyu.rewire.core.settings.NotificationCategory
import com.aiyu.rewire.core.settings.Settings
import com.aiyu.rewire.core.settings.UserProfile
import com.aiyu.rewire.domain.analytics.HabitEvent
import com.aiyu.rewire.domain.focus.FocusConfig
import com.aiyu.rewire.domain.focus.FocusSession
import com.aiyu.rewire.domain.focus.FocusState
import com.aiyu.rewire.domain.habit.Habit
import com.aiyu.rewire.domain.habit.HabitProfile
import com.aiyu.rewire.domain.habit.ProtectedApp
import com.aiyu.rewire.domain.habit.RestrictionRule
import com.aiyu.rewire.domain.warning.Warning

fun HabitWithDetails.toDomain(): HabitProfile? {
    val r = rule ?: return null // a habit without a rule can't be enforced; never surface half a row
    return HabitProfile(
        habit = Habit(habit.id, habit.name, habit.description, habit.enabled),
        apps = apps.map { ProtectedApp(it.packageName, it.habitId, it.warningLevel, it.enabled) },
        rule = RestrictionRule(r.id, r.habitId, r.dailyLimitMinutes, r.allowedStartMinutes, r.allowedEndMinutes, r.maxLaunches, r.warningLevel, r.pauseSeconds, r.escalationEnabled, r.escalationMajorMinutes, r.escalationMaxMinutes),
    )
}

fun HabitProfile.toHabitEntity(createdAt: Long) = HabitEntity(habit.id, habit.name, habit.description, habit.enabled, createdAt)
fun HabitProfile.toAppEntities() = apps.map { ProtectedAppEntity(id, it.packageName, it.warningLevel, it.enabled) }
fun HabitProfile.toRuleEntity() = with(rule) {
    RestrictionRuleEntity(this.id, habitId, dailyLimitMinutes, allowedStartMinutes, allowedEndMinutes, maxLaunches, warningLevel, pauseSeconds, escalationEnabled, escalationMajorAfterMinutes, escalationMaxAfterMinutes)
}

fun WarningEntity.toDomain() = Warning(id, category, level, title, message, motivationalMessage, enabled, favorite, custom)
fun Warning.toEntity() = WarningEntity(id, category, level, title, message, motivationalMessage, enabled, favorite, custom)

fun HabitEventEntity.toDomain() = HabitEvent(id, type, packageName, habitId, timestamp, metadata)
fun HabitEvent.toEntity() = HabitEventEntity(id, type, packageName, habitId, timestamp, metadata)

fun FocusSessionEntity.toDomain() = FocusSession(
    id = id,
    state = FocusState(
        status = status,
        config = FocusConfig(focusMinutes, breakMinutes, cycles, unitMillis),
        cycle = cycle,
        phaseEndsAt = phaseEndsAt,
        pausedRemaining = pausedRemaining,
        pausedFrom = pausedFrom,
        startedAt = startedAt,
        completedAt = endedAt,
    ),
    focusedMillis = focusedMillis,
    note = note,
)

fun FocusSession.toEntity() = with(state) {
    FocusSessionEntity(
        id = id,
        focusMinutes = config.focusMinutes,
        breakMinutes = config.breakMinutes,
        cycles = config.cycles,
        unitMillis = config.unitMillis,
        status = status,
        cycle = cycle,
        phaseEndsAt = phaseEndsAt,
        pausedRemaining = pausedRemaining,
        pausedFrom = pausedFrom,
        startedAt = checkNotNull(startedAt) { "Only started sessions are stored" },
        endedAt = completedAt,
        focusedMillis = focusedMillis,
        note = note,
    )
}

fun SettingsEntity.toDomain() = Settings(
    onboardingDone = onboardingDone,
    themeMode = themeMode,
    dynamicColor = dynamicColor,
    notifications = mapOf(
        NotificationCategory.FOCUS to notifyFocus,
        NotificationCategory.GUARD to notifyGuard,
        NotificationCategory.SUMMARY to notifySummary,
    ),
    focusBypass = FocusBypass(bypassMinor, bypassMajor, bypassMax),
    focusDndEnabled = focusDndEnabled,
    notificationPermissionAsked = notificationPermissionAsked,
    profile = UserProfile(userName, userGoal, userReason, avatarShape),
    updatesEnabled = updatesEnabled,
    updateChannel = updateChannel,
    updateSnoozedUntil = updateSnoozedUntil,
    updateLastChecked = updateLastChecked,
)

fun SettingsEntity.withNotification(c: NotificationCategory, on: Boolean) = when (c) {
    NotificationCategory.FOCUS -> copy(notifyFocus = on)
    NotificationCategory.GUARD -> copy(notifyGuard = on)
    NotificationCategory.SUMMARY -> copy(notifySummary = on)
}
