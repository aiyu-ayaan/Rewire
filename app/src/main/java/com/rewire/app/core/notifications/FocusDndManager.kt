package com.rewire.app.core.notifications

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

/**
 * Manages Do Not Disturb (Notification Interruption Filter) during Focus sessions.
 * Silences all messages and alerts while allowing incoming calls from any application.
 */
class FocusDndManager(private val context: Context) {

    private val nm: NotificationManager? = context.getSystemService(NotificationManager::class.java)
    private val prefs = context.getSharedPreferences("rewire_focus_dnd", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "FocusDndManager"
        private const val KEY_APPLIED = "applied"
        private const val KEY_SAVED_FILTER = "saved_filter"
        private const val KEY_SAVED_CATEGORIES = "saved_categories"
        private const val KEY_SAVED_CALL_SENDERS = "saved_call_senders"
        private const val KEY_SAVED_MSG_SENDERS = "saved_msg_senders"
    }

    val isAccessGranted: Boolean
        get() = nm?.isNotificationPolicyAccessGranted == true

    fun dndSettingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * Applies Focus DND mode:
     * - Interruption filter set to PRIORITY
     * - Allows only phone and incoming app calls from any sender
     * - Silences all messages, notifications, and non-call alerts
     */
    @Synchronized
    fun applyFocusDnd(): Boolean {
        val manager = nm ?: return false
        if (!manager.isNotificationPolicyAccessGranted) {
            Log.d(TAG, "Notification policy access not granted; skipping Focus DND")
            return false
        }

        try {
            if (!prefs.getBoolean(KEY_APPLIED, false)) {
                val currentFilter = manager.currentInterruptionFilter
                val currentPolicy = manager.notificationPolicy
                prefs.edit().apply {
                    putBoolean(KEY_APPLIED, true)
                    putInt(KEY_SAVED_FILTER, currentFilter)
                    if (currentPolicy != null) {
                        putInt(KEY_SAVED_CATEGORIES, currentPolicy.priorityCategories)
                        putInt(KEY_SAVED_CALL_SENDERS, currentPolicy.priorityCallSenders)
                        putInt(KEY_SAVED_MSG_SENDERS, currentPolicy.priorityMessageSenders)
                    }
                    apply()
                }
            }

            // Calls allowed from ANY sender; messages silenced completely (omitted from priorityCategories)
            val focusPolicy = NotificationManager.Policy(
                NotificationManager.Policy.PRIORITY_CATEGORY_CALLS or
                    NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS,
                NotificationManager.Policy.PRIORITY_SENDERS_ANY,
                0,
            )

            manager.notificationPolicy = focusPolicy
            manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            Log.i(TAG, "Focus DND applied: calls allowed from ANY sender, all messages silenced")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply Focus DND", e)
            return false
        }
    }

    /**
     * Restores the previous interruption filter and notification policy.
     */
    @Synchronized
    fun restoreDnd(): Boolean {
        val manager = nm ?: return false
        if (!manager.isNotificationPolicyAccessGranted) return false

        try {
            if (!prefs.getBoolean(KEY_APPLIED, false)) return false

            val savedFilter = prefs.getInt(KEY_SAVED_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL)
            val restoreFilter = if (savedFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
                savedFilter
            } else {
                NotificationManager.INTERRUPTION_FILTER_ALL
            }

            if (prefs.contains(KEY_SAVED_CATEGORIES)) {
                val restoredPolicy = NotificationManager.Policy(
                    prefs.getInt(KEY_SAVED_CATEGORIES, 0),
                    prefs.getInt(KEY_SAVED_CALL_SENDERS, NotificationManager.Policy.PRIORITY_SENDERS_ANY),
                    prefs.getInt(KEY_SAVED_MSG_SENDERS, 0),
                )
                runCatching { manager.notificationPolicy = restoredPolicy }
            }

            manager.setInterruptionFilter(restoreFilter)
            prefs.edit().clear().apply()
            Log.i(TAG, "Focus DND restored to filter: $restoreFilter")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore DND", e)
            return false
        }
    }
}
