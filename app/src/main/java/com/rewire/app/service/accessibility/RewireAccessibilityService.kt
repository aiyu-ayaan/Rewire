package com.rewire.app.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.content.Intent
import com.rewire.app.core.guard.HabitEngine
import com.rewire.app.core.notifications.RewireNotifier
import com.rewire.app.data.HabitRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Platform-only: reports which app came to the foreground. No rules, no logging, no window content.
 * Runs whether or not Rewire's UI is open; the system (re)binds it after process death and reboot.
 */
@AndroidEntryPoint
class RewireAccessibilityService : AccessibilityService() {
    @Inject lateinit var engine: HabitEngine
    @Inject lateinit var notifier: RewireNotifier
    @Inject lateinit var habits: HabitRepository

    override fun onServiceConnected() {
        running.value = true
        notifier.cancelProtectionOff()
        engine.resync()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        running.value = false
        if (habits.habits.value.any { it.habit.enabled }) notifier.protectionOff()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        running.value = false
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        engine.onForeground(pkg, event.className?.toString())
    }

    override fun onInterrupt() = Unit

    companion object {
        private val running = MutableStateFlow(false)
        /** True while the system has the service connected in this process. */
        val isRunning: StateFlow<Boolean> = running.asStateFlow()
    }
}
