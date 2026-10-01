package com.rewire.app.service.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rewire.app.data.HabitRepository
import com.rewire.app.service.monitoring.GuardMonitorService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Restores required monitoring state after device reboot (AGENTS.md §17).
 * Light check: only starts foreground service if habits are enabled.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var habits: HabitRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // A session interrupted by the reboot was already resumed (or its DND undone) in RewireApp.onCreate.
            if (habits.habits.value.any { it.habit.enabled }) {
                GuardMonitorService.start(context)
            }
        }
    }
}
