package com.rewire.app

import android.app.Application

class RewireApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.createChannels()
        // A previous process may have died mid-session; its ongoing notification would lie.
        container.notifier.cancelFocusOngoing()
    }
}
