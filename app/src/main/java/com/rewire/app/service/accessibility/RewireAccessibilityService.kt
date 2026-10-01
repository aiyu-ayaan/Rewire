package com.rewire.app.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.content.Intent
import com.rewire.app.RewireApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Platform-only: reports which app came to the foreground. No rules, no logging, no window content.
 * Runs whether or not Rewire's UI is open; the system (re)binds it after process death and reboot.
 */
class RewireAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        running.value = true
        val c = (application as RewireApp).container
        c.notifier.cancelProtectionOff()
        c.engine.resync()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        running.value = false
        val c = (application as RewireApp).container
        if (c.habits.habits.value.any { it.habit.enabled }) c.notifier.protectionOff()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        running.value = false
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        (application as RewireApp).container.engine.onForeground(pkg, event.className?.toString())
    }

    override fun onInterrupt() = Unit

    companion object {
        private val running = MutableStateFlow(false)
        /** True while the system has the service connected in this process. */
        val isRunning: StateFlow<Boolean> = running.asStateFlow()
    }
}
