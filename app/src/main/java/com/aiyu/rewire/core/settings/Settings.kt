package com.aiyu.rewire.core.settings

import com.aiyu.rewire.data.local.SettingsDao
import com.aiyu.rewire.R
import androidx.annotation.StringRes
import com.aiyu.rewire.domain.update.UpdateChannel
import com.aiyu.rewire.data.local.SettingsEntity
import com.aiyu.rewire.data.local.toDomain
import com.aiyu.rewire.data.local.withNotification
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** User-controllable notification categories. Each maps to one channel. */
enum class NotificationCategory { FOCUS, GUARD, SUMMARY }

/** What the user is working on. Drives copy now, warning suggestions later. */
enum class UserGoal(@StringRes val label: Int) {
    LESS_SCROLLING(R.string.goal_less_scrolling),
    DEEP_WORK(R.string.notif_focus_title),
    STUDY(R.string.goal_study),
    SLEEP(R.string.goal_sleep),
    LESS_GAMING(R.string.goal_less_gaming),
    SPEND_LESS(R.string.goal_spend_less),
}

@Serializable
data class UserProfile(
    val name: String,
    val goal: UserGoal?,
    /** User's own words, shown back to them in warnings and Profile. */
    val reason: String,
    /** Index into the avatar shape list (ui layer owns the shapes). */
    val avatarShape: Int,
)

@Serializable
data class FocusBypass(val minor: Boolean, val major: Boolean, val max: Boolean)

@Serializable
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
