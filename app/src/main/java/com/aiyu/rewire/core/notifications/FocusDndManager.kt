package com.aiyu.rewire.core.notifications

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import android.util.Log

/**
 * Manages Do Not Disturb (Notification Interruption Filter) during Focus sessions.
 * Silences notifications and messages; calls (any sender), alarms and media still come through.
 */
class FocusDndManager(private val context: Context) {

    private val nm: NotificationManager? = context.getSystemService(NotificationManager::class.java)
    private val audioManager: AudioManager? = context.getSystemService(AudioManager::class.java)
    private val prefs = context.getSharedPreferences("rewire_focus_dnd", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "FocusDndManager"
        private const val KEY_APPLIED = "applied"
        private const val KEY_SAVED_FILTER = "saved_filter"
        private const val KEY_SAVED_CATEGORIES = "saved_categories"
        private const val KEY_SAVED_CALL_SENDERS = "saved_call_senders"
        private const val KEY_SAVED_MSG_SENDERS = "saved_msg_senders"
        private const val KEY_SAVED_CONV_SENDERS = "saved_conv_senders"
        private const val KEY_SAVED_NOTIF_VOL = "saved_notif_vol"
        private const val KEY_SAVED_RING_VOL = "saved_ring_vol"
        private const val KEY_SAVED_RINGER_MODE = "saved_ringer_mode"
        private const val KEY_SAVED_NOTIF_MUTED = "saved_notif_muted"
    }

    val isAccessGranted: Boolean
        get() = nm?.isNotificationPolicyAccessGranted == true

    fun dndSettingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * Applies Focus notification muting mode:
     * - Silences notifications and messages from all apps by muting STREAM_NOTIFICATION.
     * - Leaves STREAM_RING at normal volume and ensures ringerMode is NORMAL so that
     *   both cellular phone calls and VoIP calls (like WhatsApp) ring audibly and vibrate
     *   according to the user's default system settings.
     * - Ensures Android's Interruption Filter is not in PRIORITY/SILENT mode (which forces
     *   external ringer mode to SILENT in ZenModeHelper and breaks Telecom vibration and WhatsApp audio).
     */
    @Synchronized
    fun applyFocusDnd(): Boolean {
        val manager = nm ?: return false
        if (!manager.isNotificationPolicyAccessGranted) {
            Log.d(TAG, "Notification policy access not granted; skipping Focus DND")
            return false
        }

        try {
            val audio = audioManager
            val currentFilter = manager.currentInterruptionFilter
            val currentPolicy = manager.notificationPolicy
            val currentNotifVol = runCatching { audio?.getStreamVolume(AudioManager.STREAM_NOTIFICATION) }.getOrNull() ?: -1
            val currentRingVol = runCatching { audio?.getStreamVolume(AudioManager.STREAM_RING) }.getOrNull() ?: -1
            val currentRingerMode = runCatching { audio?.ringerMode }.getOrNull() ?: AudioManager.RINGER_MODE_NORMAL
            val isNotifMuted = runCatching { audio?.isStreamMute(AudioManager.STREAM_NOTIFICATION) == true }.getOrDefault(false)

            if (!prefs.getBoolean(KEY_APPLIED, false)) {
                prefs.edit().apply {
                    putBoolean(KEY_APPLIED, true)
                    putInt(KEY_SAVED_FILTER, currentFilter)
                    putInt(KEY_SAVED_NOTIF_VOL, currentNotifVol)
                    putInt(KEY_SAVED_RING_VOL, currentRingVol)
                    putInt(KEY_SAVED_RINGER_MODE, currentRingerMode)
                    putBoolean(KEY_SAVED_NOTIF_MUTED, isNotifMuted)
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

            // 1. Mute notifications & messages
            if (audio != null) {
                runCatching {
                    audio.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
                    audio.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0)
                }

                // 2. Ensure incoming calls (STREAM_RING) remain audible and unmuted
                runCatching {
                    audio.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0)
                    val activeRingVol = audio.getStreamVolume(AudioManager.STREAM_RING)
                    val savedRingVol = prefs.getInt(KEY_SAVED_RING_VOL, -1)
                    // If setting notification volume lowered ring volume (OEMs with linked streams), restore ring volume:
                    if (activeRingVol == 0 && savedRingVol > 0) {
                        audio.setStreamVolume(AudioManager.STREAM_RING, savedRingVol, 0)
                    }
                }

                // 3. Ensure ringer mode is NORMAL so Telecom and WhatsApp play ringtones and vibrate
                runCatching {
                    if (audio.ringerMode == AudioManager.RINGER_MODE_SILENT && currentRingerMode != AudioManager.RINGER_MODE_SILENT) {
                        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    }
                }
            }

            // 4. Ensure Interruption Filter is INTERRUPTION_FILTER_ALL so ZenModeHelper does not
            // force mRingerModeExternal into SILENT (which kills Telecom vibration and WhatsApp audio).
            runCatching {
                if (manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) {
                    manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                }
            }

            // 5. Keep priority policy configured as a fallback
            var priorityCategories = NotificationManager.Policy.PRIORITY_CATEGORY_CALLS or
                NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                priorityCategories = priorityCategories or
                    NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS or
                    NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA or
                    NotificationManager.Policy.PRIORITY_CATEGORY_SYSTEM
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                priorityCategories = priorityCategories or
                    NotificationManager.Policy.PRIORITY_CATEGORY_CONVERSATIONS
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
            runCatching { manager.notificationPolicy = focusPolicy }

            Log.i(TAG, "Focus muting applied: notifications silenced, calls audible with vibration")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply Focus DND", e)
            return false
        }
    }

    /**
     * Ensures incoming calls (cellular and WhatsApp VoIP) ring audibly and vibrate normally.
     * Can be invoked when an incoming call is ringing or when call UI is displayed.
     */
    fun ensureCallRinging(): Boolean {
        val audio = audioManager ?: return false
        return runCatching {
            audio.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0)
            if (audio.ringerMode == AudioManager.RINGER_MODE_SILENT) {
                audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }
            nm?.let { m ->
                if (m.isNotificationPolicyAccessGranted && m.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) {
                    m.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                }
            }
            true
        }.getOrDefault(false)
    }

    /**
     * Restores the previous notification volume, ringer mode, and interruption filter.
     */
    @Synchronized
    fun restoreDnd(): Boolean {
        val manager = nm ?: return false
        if (!manager.isNotificationPolicyAccessGranted) return false

        try {
            if (!prefs.getBoolean(KEY_APPLIED, false)) return false

            val audio = audioManager

            if (audio != null) {
                // 1. Restore notification stream volume
                if (prefs.contains(KEY_SAVED_NOTIF_VOL)) {
                    val savedNotifVol = prefs.getInt(KEY_SAVED_NOTIF_VOL, -1)
                    val savedNotifMuted = prefs.getBoolean(KEY_SAVED_NOTIF_MUTED, false)
                    runCatching {
                        if (savedNotifVol >= 0) {
                            audio.setStreamVolume(AudioManager.STREAM_NOTIFICATION, savedNotifVol, 0)
                        }
                        if (!savedNotifMuted) {
                            audio.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
                        }
                    }
                }

                // 2. Restore ring stream volume and ringer mode
                if (prefs.contains(KEY_SAVED_RING_VOL)) {
                    val savedRingVol = prefs.getInt(KEY_SAVED_RING_VOL, -1)
                    if (savedRingVol >= 0) {
                        runCatching {
                            audio.setStreamVolume(AudioManager.STREAM_RING, savedRingVol, 0)
                        }
                    }
                }
                runCatching {
                    audio.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0)
                }
                if (prefs.contains(KEY_SAVED_RINGER_MODE)) {
                    val savedRingerMode = prefs.getInt(KEY_SAVED_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL)
                    runCatching { audio.ringerMode = savedRingerMode }
                }
            }

            // 3. Restore original notification policy if saved
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

            // 4. Restore interruption filter if the user originally had a non-ALL filter
            val savedFilter = prefs.getInt(KEY_SAVED_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL)
            runCatching {
                manager.setInterruptionFilter(
                    if (savedFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN) savedFilter
                    else NotificationManager.INTERRUPTION_FILTER_ALL
                )
            }

            prefs.edit().clear().apply()
            Log.i(TAG, "Focus notification muting restored")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore DND", e)
            return false
        }
    }
}
