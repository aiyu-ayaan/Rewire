package com.rewire.app.service.focus

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.rewire.app.RewireApp
import com.rewire.app.core.notifications.RewireNotifier
import com.rewire.app.domain.focus.FocusState
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps a focus session alive when the app is swiped from recents, minimised or the screen is off.
 * Pure platform glue: the timer itself is [com.rewire.app.core.focus.FocusController]; this service
 * only holds the foreground notification and a wake lock so phase changes fire on time.
 */
class FocusTimerService : Service() {

    private val scope = MainScope()
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        val c = (application as RewireApp).container
        // startForeground must run within seconds of startForegroundService, before anything else.
        promote(c.notifier, c.focus.current.value)
        scope.launch {
            c.focus.current.collect { s ->
                if (!s.isActive) {
                    stopSelf()
                    return@collect
                }
                promote(c.notifier, s)
                holdWakeLock(s)
            }
        }
    }

    // Sticky: if Android kills the process, it recreates the service and RewireApp restores the session from Room.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val c = (application as RewireApp).container
        val s = c.focus.current.value
        promote(c.notifier, s) // every startForegroundService needs a matching startForeground
        if (!s.isActive) stopSelf()
        return START_STICKY
    }

    private fun promote(notifier: RewireNotifier, s: FocusState) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, RewireNotifier.Ids.FOCUS_ONGOING, notifier.focusOngoing(s, System.currentTimeMillis()), type)
    }

    /**
     * CPU stays awake only until the current phase ends (+ slack), so the chime rings on time in Doze.
     * Paused = no wake lock.
     */
    // ponytail: wake lock per running phase; exact AlarmManager alarms if battery cost shows up in vitals.
    private fun holdWakeLock(s: FocusState) {
        wakeLock?.takeIf { it.isHeld }?.release()
        if (!s.isRunning) return
        val pm = getSystemService(PowerManager::class.java) ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "rewire:focus").apply {
            setReferenceCounted(false)
            acquire(s.remaining(System.currentTimeMillis()) + WAKE_SLACK_MILLIS)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        (application as RewireApp).container.notifier.cancelFocusOngoing()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val WAKE_SLACK_MILLIS = 10_000L

        fun sync(context: Context, active: Boolean) {
            val intent = Intent(context, FocusTimerService::class.java)
            // Background start can be refused (Android 12+); the session is in Room and resumes on next app start.
            runCatching { if (active) ContextCompat.startForegroundService(context, intent) else context.stopService(intent) }
        }
    }
}
