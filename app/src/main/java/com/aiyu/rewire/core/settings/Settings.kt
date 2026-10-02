package com.rewire.app.core.settings

import com.rewire.app.data.local.SettingsDao
import com.rewire.app.domain.update.UpdateChannel
import com.rewire.app.data.local.SettingsEntity
import com.rewire.app.data.local.toDomain
import com.rewire.app.data.local.withNotification
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** User-controllable notification categories. Each maps to one channel. */
enum class NotificationCategory { FOCUS, GUARD, SUMMARY }

/** What the user is working on. Drives copy now, warning suggestions later. */
enum class UserGoal(val label: String) {
    LESS_SCROLLING("Scroll less"),
    DEEP_WORK("Deep work"),
    STUDY("Study more"),
    SLEEP("Sleep better"),
    LESS_GAMING("Game less"),
    SPEND_LESS("Spend less"),
}

data class UserProfile(
    val name: String,
    val goal: UserGoal?,
    /** User's own words, shown back to them in warnings and Profile. */
    val reason: String,
    /** Index into the avatar shape list (ui layer owns the shapes). */
    val avatarShape: Int,
) {
    val displayName get() = name.ifBlank { "You" }
}

data class FocusBypass(val minor: Boolean, val major: Boolean, val max: Boolean)

data class Settings(
    val onboardingDone: Boolean,
    val themeMode: ThemeMode,
    val dynamicColor: Boolean,
    val notifications: Map<NotificationCategory, Boolean>,
    val focusBypass: FocusBypass,
    /** Silences all messages while allowing incoming calls from any app during focus sessions. */
    val focusDndEnabled: Boolean,
    /** Asked for POST_NOTIFICATIONS at least once — after that, recovery goes through system settings. */
    val notificationPermissionAsked: Boolean,
    val profile: UserProfile,
    val updatesEnabled: Boolean = true,
    /** null = follow this build's own channel. */
    val updateChannel: UpdateChannel? = null,
    val updateSnoozedUntil: Long = 0,
    val updateLastChecked: Long = 0,
)

class SettingsRepository(private val dao: SettingsDao) {

    val settings: Flow<Settings> = dao.observe().map { (it ?: SettingsEntity()).toDomain() }

    /** Synchronous first read so services see real settings before the Flow emits. */
    suspend fun current(): Settings = (dao.get() ?: SettingsEntity()).toDomain()

    suspend fun setOnboardingDone() = dao.edit { it.copy(onboardingDone = true) }
    suspend fun setThemeMode(mode: ThemeMode) = dao.edit { it.copy(themeMode = mode) }
    suspend fun setDynamicColor(on: Boolean) = dao.edit { it.copy(dynamicColor = on) }
    suspend fun setNotification(c: NotificationCategory, on: Boolean) = dao.edit { it.withNotification(c, on) }
    suspend fun setNotificationPermissionAsked() = dao.edit { it.copy(notificationPermissionAsked = true) }
    suspend fun setProfile(p: UserProfile) = dao.edit {
        it.copy(userName = p.name.trim(), userGoal = p.goal, userReason = p.reason.trim(), avatarShape = p.avatarShape)
    }
    suspend fun setFocusBypass(b: FocusBypass) = dao.edit { it.copy(bypassMinor = b.minor, bypassMajor = b.major, bypassMax = b.max) }
    suspend fun setUpdatesEnabled(on: Boolean) = dao.edit { it.copy(updatesEnabled = on) }
    suspend fun setUpdateChannel(channel: UpdateChannel) = dao.edit { it.copy(updateChannel = channel) }
    suspend fun setUpdateSnoozedUntil(at: Long) = dao.edit { it.copy(updateSnoozedUntil = at) }
    suspend fun setUpdateLastChecked(at: Long) = dao.edit { it.copy(updateLastChecked = at) }
    suspend fun setFocusDndEnabled(enabled: Boolean) = dao.edit { it.copy(focusDndEnabled = enabled) }
}
