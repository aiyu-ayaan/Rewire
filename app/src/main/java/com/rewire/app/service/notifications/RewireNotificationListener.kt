package com.rewire.app.service.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.rewire.app.RewireApp

/**
 * Platform-only: hides notifications from apps that are Max-blocked right now.
 * The decision comes from HabitEngine; content is never read or stored.
 */
class RewireNotificationListener : NotificationListenerService() {

    private val engine get() = (application as RewireApp).container.engine

    override fun onListenerConnected() {
        instance = this
        sweep()
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (engine.silenceNotificationFrom(sbn.packageName)) cancelNotification(sbn.key)
    }

    /** Clear what's already in the shade when a block starts. */
    fun sweep() {
        runCatching { activeNotifications }.getOrNull()?.forEach { onNotificationPosted(it) }
    }

    companion object {
        /** Set while the system has us bound; lets the engine sweep when a block fires. */
        @Volatile var instance: RewireNotificationListener? = null
            private set
    }
}
