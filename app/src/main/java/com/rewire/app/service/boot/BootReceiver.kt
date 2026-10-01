package com.rewire.app.service.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rewire.app.RewireApp
import com.rewire.app.service.monitoring.GuardMonitorService

/**
 * Restores required monitoring state after device reboot (AGENTS.md §17).
 * Light check: only starts foreground service if habits are enabled.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val app = context.applicationContext as? RewireApp ?: return
            val habits = app.container.habits.habits.value
            if (habits.any { it.habit.enabled }) {
                GuardMonitorService.start(context)
            }
        }
    }
}
