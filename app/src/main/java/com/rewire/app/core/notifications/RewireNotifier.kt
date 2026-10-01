package com.rewire.app.core.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RawRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.rewire.app.MainActivity
import com.rewire.app.R
import com.rewire.app.core.settings.NotificationCategory
import com.rewire.app.domain.focus.FocusSessionStatus
import com.rewire.app.domain.focus.FocusState

/** Top-level destinations a notification can open. */
enum class DeepLink { GUARD, FOCUS, MATRIX, PROFILE }

enum class FocusAlert { BREAK_STARTED, FOCUS_RESUMED, COMPLETED }

/**
 * Single entry point for every notification. Checks system permission + the user's per-category
 * preference before posting, so callers never need to.
 */
class RewireNotifier(
    private val context: Context,
    private val categoryEnabled: (NotificationCategory) -> Boolean,
) {
    private val manager = NotificationManagerCompat.from(context)

    object Channels {
        const val FOCUS_SESSION = "focus_session"
        const val FOCUS_ALERTS = "focus_alerts"
        const val GUARD = "guard"
        const val SUMMARY = "daily_summary"
        const val SYSTEM = "system"
    }

    object Ids {
        const val FOCUS_ONGOING = 1001
        const val FOCUS_ALERT = 1002
        const val GUARD_ONGOING = 1003
        const val TEST = 1900
        const val PROTECTION_OFF = 1500
    }

    fun createChannels() {
        fun channel(id: String, importance: Int, name: Int, desc: Int, silent: Boolean = false) =
            NotificationChannelCompat.Builder(id, importance)
                .setName(context.getString(name))
                .setDescription(context.getString(desc))
                .apply { if (silent) setSound(null, null).setVibrationEnabled(false) }
                .build()
        manager.createNotificationChannelsCompat(
            listOf(
                channel(Channels.FOCUS_SESSION, NotificationManagerCompat.IMPORTANCE_LOW, R.string.channel_focus_session, R.string.channel_focus_session_desc, silent = true),
                channel(Channels.FOCUS_ALERTS, NotificationManagerCompat.IMPORTANCE_HIGH, R.string.channel_focus_alerts, R.string.channel_focus_alerts_desc),
                channel(Channels.GUARD, NotificationManagerCompat.IMPORTANCE_DEFAULT, R.string.channel_guard, R.string.channel_guard_desc),
                channel(Channels.SUMMARY, NotificationManagerCompat.IMPORTANCE_LOW, R.string.channel_summary, R.string.channel_summary_desc),
                channel(Channels.SYSTEM, NotificationManagerCompat.IMPORTANCE_DEFAULT, R.string.channel_system, R.string.channel_system_desc),
            )
        )
    }

    fun hasPermission(): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            manager.areNotificationsEnabled()

    // ---- Focus -------------------------------------------------------------------------------

    fun showFocusOngoing(state: FocusState, now: Long) {
        if (!state.isActive) return cancelFocusOngoing()
        val onBreak = state.phase == FocusSessionStatus.BREAK
        val title = context.getString(if (onBreak) R.string.notif_focus_break_title else R.string.notif_focus_title)
        val session = context.getString(R.string.notif_focus_session, state.cycle, state.config.cycles)
        val builder = base(Channels.FOCUS_SESSION, DeepLink.FOCUS)
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPublicVersion(publicVersion(Channels.FOCUS_SESSION, title))
        if (state.status == FocusSessionStatus.PAUSED) {
            builder.setContentText(context.getString(R.string.notif_focus_paused, formatRemaining(state.remaining(now)), session))
                .setShowWhen(false)
        } else {
            builder.setContentText(session)
                .setWhen(state.phaseEndsAt ?: now)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        }
        post(NotificationCategory.FOCUS, Ids.FOCUS_ONGOING, builder.build())
    }

    fun cancelFocusOngoing() = manager.cancel(Ids.FOCUS_ONGOING)

    fun focusAlert(alert: FocusAlert, state: FocusState) {
        val (title, text) = when (alert) {
            FocusAlert.BREAK_STARTED -> R.string.notif_break_started_title to context.getString(R.string.notif_break_started_text, state.config.breakMinutes)
            FocusAlert.FOCUS_RESUMED -> R.string.notif_focus_resumed_title to context.getString(R.string.notif_focus_session, state.cycle, state.config.cycles)
            FocusAlert.COMPLETED -> R.string.notif_completed_title to context.getString(R.string.notif_completed_text, state.config.cycles, state.config.focusMinutes * state.config.cycles)
        }
        val n = base(Channels.FOCUS_ALERTS, DeepLink.FOCUS)
            .setContentTitle(context.getString(title))
            .setContentText(text)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSilent(true) // the chimes are the audible cue; avoid a double sound
            .build()
        post(NotificationCategory.FOCUS, Ids.FOCUS_ALERT, n)
    }

    /** Rising chime: focus block ended, break (or session end) begins. */
    fun playBreakTone() = playTone(R.raw.break_tone)

    /** Answering chime: break is over, focus resumes. */
    fun playFocusTone() = playTone(R.raw.focus_tone)

    private fun playTone(@RawRes res: Int) {
        runCatching {
            val uri = Uri.parse("android.resource://${context.packageName}/$res")
            RingtoneManager.getRingtone(context, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                play()
            }
        }
    }

    // ---- Misc --------------------------------------------------------------------------------

    /** CLAUDE.md §28 "monitoring disabled": system channel, always on, opens Guard. */
    fun protectionOff() {
        val n = base(Channels.SYSTEM, DeepLink.GUARD)
            .setContentTitle(context.getString(R.string.notif_protection_off_title))
            .setContentText(context.getString(R.string.notif_protection_off_text))
            .setAutoCancel(true)
            .build()
        post(null, Ids.PROTECTION_OFF, n)
    }

    fun guardOngoingNotification(): Notification =
        base(Channels.GUARD, DeepLink.GUARD)
            .setContentTitle(context.getString(R.string.notif_guard_active_title))
            .setContentText(context.getString(R.string.notif_guard_active_text))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    fun cancelProtectionOff() = manager.cancel(Ids.PROTECTION_OFF)

    /** Lets user verify delivery from Profile → Notifications. Bypasses category prefs on purpose. */
    fun sendTest(): Boolean {
        val n = base(Channels.SYSTEM, DeepLink.PROFILE)
            .setContentTitle(context.getString(R.string.notif_test_title))
            .setContentText(context.getString(R.string.notif_test_text))
            .setAutoCancel(true)
            .build()
        return post(null, Ids.TEST, n)
    }

    fun channelEnabled(channelId: String): Boolean =
        manager.getNotificationChannelCompat(channelId)?.importance != NotificationManagerCompat.IMPORTANCE_NONE

    fun appSettingsIntent(): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    // ---- internals ---------------------------------------------------------------------------

    @SuppressLint("MissingPermission") // checked in hasPermission()
    private fun post(category: NotificationCategory?, id: Int, n: Notification): Boolean {
        if (!hasPermission()) return false
        if (category != null && !categoryEnabled(category)) return false
        manager.notify(id, n)
        return true
    }

    private fun base(channel: String, link: DeepLink) = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_stat_rewire)
        .setColor(ContextCompat.getColor(context, R.color.notification_accent))
        .setContentIntent(contentIntent(link))

    private fun publicVersion(channel: String, title: String) = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_stat_rewire)
        .setContentTitle(title)
        .build()

    private fun contentIntent(link: DeepLink): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_DEEP_LINK, link.name)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, link.ordinal, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        const val EXTRA_DEEP_LINK = "com.rewire.app.DEEP_LINK"

        fun formatRemaining(millis: Long): String {
            val total = (millis + 999) / 1000
            return "%d:%02d".format(total / 60, total % 60)
        }
    }
}
