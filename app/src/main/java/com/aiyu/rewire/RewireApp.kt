package com.aiyu.rewire

import android.app.Application
import com.aiyu.rewire.core.focus.FocusController
import com.aiyu.rewire.core.guard.HabitEngine
import com.aiyu.rewire.core.analytics.SummaryWorker
import com.aiyu.rewire.core.update.AppUpdater
import com.aiyu.rewire.core.update.UpdateWorker
import com.aiyu.rewire.core.notifications.RewireNotifier
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class RewireApp : Application() {
    @Inject lateinit var notifier: RewireNotifier
    @Inject lateinit var focus: FocusController
    @Inject lateinit var dnd: com.aiyu.rewire.core.notifications.FocusDndManager
    @Inject lateinit var updater: dagger.Lazy<AppUpdater> // Lazy: never built when BuildConfig.UPDATES is off (play)
    // Eager: the engine starts GuardMonitorService once a habit is on. Lite (and Full with Accessibility off)
    // has no accessibility service to create it, so without this nothing ever watched.
    @Inject lateinit var engine: HabitEngine

    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(com.aiyu.rewire.core.settings.AppLocale.wrap(base))

    @Inject lateinit var warnings: com.aiyu.rewire.data.WarningRepository

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        warnings.relocalize() // API 33+: the system changed the app language
    }

    override fun onCreate() {
        super.onCreate()
        notifier.createChannels()
        dnd.clearZenRulesAndEnsureFilterAll()
        // A previous process may have died mid-session: resume it from Room (or undo its DND).
        focus.restore()
        // Idempotent (KEEP); cancels itself when auto-update is off.
        if (BuildConfig.UPDATES) UpdateWorker.schedule(this, updater.get().enabled)
        SummaryWorker.schedule(this)
    }
}
