package com.aiyu.rewire.core.notifications

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

/**
 * Manages Do Not Disturb (Notification Interruption Filter) during Focus sessions.
 * Silences notifications and messages; calls (any sender), alarms and media still come through.
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
        private const val KEY_SAVED_CONV_SENDERS = "saved_conv_senders"
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
     * - Silences all messages, conversations, notifications, and non-call alerts
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
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            putInt(KEY_SAVED_CONV_SENDERS, currentPolicy.priorityConversationSenders)
                        }
                    }
                    apply()
                }
            }

            // Only notifications go quiet: calls from ANY sender, alarms (incl. Rewire's own timer chime),
            // system sounds and media keep working. Incoming calls (cellular & VoIP like WhatsApp) ring;
            // messages are silenced because PRIORITY_CATEGORY_MESSAGES is omitted.
            var priorityCategories = NotificationManager.Policy.PRIORITY_CATEGORY_CALLS or
                NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                priorityCategories = priorityCategories or
                    NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS or
                    NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA or
                    NotificationManager.Policy.PRIORITY_CATEGORY_SYSTEM
            }

            val focusPolicy = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                NotificationManager.Policy(
                    priorityCategories,
                    NotificationManager.Policy.PRIORITY_SENDERS_ANY,
                    NotificationManager.Policy.PRIORITY_SENDERS_ANY,
                    0,
                    NotificationManager.Policy.CONVERSATION_SENDERS_ANYONE,
                )
            } else {
                NotificationManager.Policy(
                    priorityCategories,
                    NotificationManager.Policy.PRIORITY_SENDERS_ANY,
                    NotificationManager.Policy.PRIORITY_SENDERS_ANY,
                )
            }

            manager.notificationPolicy = focusPolicy
            manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            Log.i(TAG, "Focus DND applied: calls, alarms and media allowed; notifications silenced")
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
                val savedCategories = prefs.getInt(KEY_SAVED_CATEGORIES, 0)
                val savedCallSenders = prefs.getInt(KEY_SAVED_CALL_SENDERS, NotificationManager.Policy.PRIORITY_SENDERS_ANY)
                val savedMsgSenders = prefs.getInt(KEY_SAVED_MSG_SENDERS, 0)
                val restoredPolicy = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && prefs.contains(KEY_SAVED_CONV_SENDERS)) {
                    NotificationManager.Policy(
                        savedCategories,
                        savedCallSenders,
                        savedMsgSenders,
                        0,
                        prefs.getInt(KEY_SAVED_CONV_SENDERS, NotificationManager.Policy.CONVERSATION_SENDERS_ANYONE),
                    )
                } else {
                    NotificationManager.Policy(
                        savedCategories,
                        savedCallSenders,
                        savedMsgSenders,
                    )
                }
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
