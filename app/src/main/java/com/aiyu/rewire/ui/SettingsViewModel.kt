package com.aiyu.rewire.ui

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiyu.rewire.core.focus.FocusController
import com.aiyu.rewire.core.notifications.FocusDndManager
import com.aiyu.rewire.core.notifications.RewireNotifier
import com.aiyu.rewire.core.settings.NotificationCategory
import com.aiyu.rewire.core.settings.Settings
import com.aiyu.rewire.core.settings.SettingsRepository
import com.aiyu.rewire.core.settings.ThemeMode
import com.aiyu.rewire.core.settings.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Settings + notification / DND status for every screen that shows or edits preferences
 * (Profile, Notifications, onboarding, permission rationale). Writes go to Room via the repository.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    val settings: StateFlow<Settings?>,
    private val repo: SettingsRepository,
    private val notifier: RewireNotifier,
    private val dnd: FocusDndManager,
    private val focus: FocusController,
) : ViewModel() {

    fun setThemeMode(mode: ThemeMode) = launch { repo.setThemeMode(mode) }
    fun setDynamicColor(on: Boolean) = launch { repo.setDynamicColor(on) }
    fun setNotification(c: NotificationCategory, on: Boolean) = launch { repo.setNotification(c, on) }
    fun setNotificationPermissionAsked() = launch { repo.setNotificationPermissionAsked() }
    fun setOnboardingDone() = launch { repo.setOnboardingDone() }

    fun setProfile(p: UserProfile, then: () -> Unit) = launch { repo.setProfile(p); then() }

    fun setFocusDnd(on: Boolean) = launch {
        repo.setFocusDndEnabled(on)
        focus.onDndSettingChanged(on)
    }

    val hasNotificationPermission: Boolean get() = notifier.hasPermission()
    fun channelEnabled(id: String) = notifier.channelEnabled(id)
    fun sendTestNotification() = notifier.sendTest()
    fun appNotificationSettingsIntent(): Intent = notifier.appSettingsIntent()

    val isDndAccessGranted: Boolean get() = dnd.isAccessGranted
    fun dndSettingsIntent(): Intent = dnd.dndSettingsIntent()

    private fun launch(block: suspend () -> Unit) { viewModelScope.launch { block() } }
}
