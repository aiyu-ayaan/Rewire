package com.rewire.app.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Platform-only: reports which app came to the foreground. No rules, no logging, no window content.
 * Phase 3 connects [ForegroundAppDetector.foreground] to the HabitEngine.
 */
class RewireAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        event.packageName?.toString()?.let(ForegroundAppDetector::onForeground)
    }

    override fun onInterrupt() = Unit
}

object ForegroundAppDetector {
    private val state = MutableStateFlow<String?>(null)
    val foreground: StateFlow<String?> = state.asStateFlow()
    fun onForeground(packageName: String) { state.value = packageName }
}
