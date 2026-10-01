package com.rewire.app

import android.app.Application
import com.rewire.app.core.focus.FocusController
import com.rewire.app.core.update.AppUpdater
import com.rewire.app.core.update.UpdateWorker
import com.rewire.app.core.notifications.RewireNotifier
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class RewireApp : Application() {
    @Inject lateinit var notifier: RewireNotifier
    @Inject lateinit var focus: FocusController
    @Inject lateinit var updater: AppUpdater

    override fun onCreate() {
        super.onCreate()
        notifier.createChannels()
        // A previous process may have died mid-session: resume it from Room (or undo its DND).
        focus.restore()
        // Idempotent (KEEP); cancels itself when auto-update is off.
        UpdateWorker.schedule(this, updater.enabled)
    }
}
