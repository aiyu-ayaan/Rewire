package com.rewire.app.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
)

private val Context.dataStore by preferencesDataStore("settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val onboarding = booleanPreferencesKey("onboarding_done")
        val theme = stringPreferencesKey("theme_mode")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val permissionAsked = booleanPreferencesKey("notif_permission_asked")
        val bypassMinor = booleanPreferencesKey("bypass_minor")
        val bypassMajor = booleanPreferencesKey("bypass_major")
        val bypassMax = booleanPreferencesKey("bypass_max")
        val focusDnd = booleanPreferencesKey("focus_dnd_enabled")
        val userName = stringPreferencesKey("user_name")
        val userGoal = stringPreferencesKey("user_goal")
        val userReason = stringPreferencesKey("user_reason")
        val avatarShape = intPreferencesKey("avatar_shape")
        fun notif(c: NotificationCategory) = booleanPreferencesKey("notif_${c.name.lowercase()}")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings() = Settings(
        onboardingDone = this[Keys.onboarding] ?: false,
        themeMode = this[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
        dynamicColor = this[Keys.dynamic] ?: false,
        notifications = NotificationCategory.entries.associateWith { this[Keys.notif(it)] ?: true },
        // CLAUDE.md §7 recommended defaults: Minor bypass on, Major configurable (off), Max off.
        focusBypass = FocusBypass(
            minor = this[Keys.bypassMinor] ?: true,
            major = this[Keys.bypassMajor] ?: false,
            max = this[Keys.bypassMax] ?: false,
        ),
        focusDndEnabled = this[Keys.focusDnd] ?: true,
        notificationPermissionAsked = this[Keys.permissionAsked] ?: false,
        profile = UserProfile(
            name = this[Keys.userName].orEmpty(),
            goal = this[Keys.userGoal]?.let { runCatching { UserGoal.valueOf(it) }.getOrNull() },
            reason = this[Keys.userReason].orEmpty(),
            avatarShape = this[Keys.avatarShape] ?: 0,
        ),
    )

    suspend fun setOnboardingDone() = context.dataStore.edit { it[Keys.onboarding] = true }
    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.theme] = mode.name }
    suspend fun setDynamicColor(on: Boolean) = context.dataStore.edit { it[Keys.dynamic] = on }
    suspend fun setNotification(c: NotificationCategory, on: Boolean) = context.dataStore.edit { it[Keys.notif(c)] = on }
    suspend fun setNotificationPermissionAsked() = context.dataStore.edit { it[Keys.permissionAsked] = true }
    suspend fun setProfile(p: UserProfile) = context.dataStore.edit {
        it[Keys.userName] = p.name.trim()
        if (p.goal != null) it[Keys.userGoal] = p.goal.name else it.remove(Keys.userGoal)
        it[Keys.userReason] = p.reason.trim()
        it[Keys.avatarShape] = p.avatarShape
    }
    suspend fun setFocusBypass(b: FocusBypass) = context.dataStore.edit {
        it[Keys.bypassMinor] = b.minor
        it[Keys.bypassMajor] = b.major
        it[Keys.bypassMax] = b.max
    }
    suspend fun setFocusDndEnabled(enabled: Boolean) = context.dataStore.edit {
        it[Keys.focusDnd] = enabled
    }
}
