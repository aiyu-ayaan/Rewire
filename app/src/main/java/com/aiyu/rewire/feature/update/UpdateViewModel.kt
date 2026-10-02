package com.aiyu.rewire.feature.update

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiyu.rewire.core.notifications.RewireNotifier
import com.aiyu.rewire.core.settings.Settings
import com.aiyu.rewire.core.settings.SettingsRepository
import com.aiyu.rewire.core.update.AppUpdater
import com.aiyu.rewire.core.update.UpdateState
import com.aiyu.rewire.core.update.UpdateWorker
import com.aiyu.rewire.domain.update.UpdateChannel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updater: AppUpdater,
    private val repo: SettingsRepository,
    private val notifier: RewireNotifier,
    @ApplicationContext private val context: Context,
    val settings: StateFlow<Settings?>,
) : ViewModel() {

    val state: StateFlow<UpdateState> = updater.state
    val offerShownInline: MutableStateFlow<Boolean> = updater.offerShownInline
    val installedName: String get() = updater.installedName
    val channel: UpdateChannel get() = updater.channel

    fun check() { viewModelScope.launch { updater.check(manual = true) } }

    fun download(s: UpdateState.Available) = updater.download(s.release, s.apk)
    fun install(s: UpdateState.Ready) = updater.install(s.file)
    fun notNow() = updater.snooze()
    fun dismiss() = updater.dismiss()

    fun canInstall() = updater.canInstall()
    fun installPermissionIntent(): Intent = updater.installPermissionIntent()

    fun setEnabled(on: Boolean) {
        viewModelScope.launch {
            repo.setUpdatesEnabled(on)
            UpdateWorker.schedule(context, on) // a switch that says "no" must not leave a daily job running
            if (!on) { updater.dismiss(); notifier.cancelUpdateAvailable() }
        }
    }

    fun setChannel(c: UpdateChannel) {
        viewModelScope.launch {
            repo.setUpdateChannel(c)
            updater.onChannelChanged()
            updater.check(manual = true, channel = c)
        }
    }
}
