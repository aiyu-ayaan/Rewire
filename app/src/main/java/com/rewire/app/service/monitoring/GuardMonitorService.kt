package com.rewire.app.service.monitoring

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.rewire.app.RewireApp
import com.rewire.app.core.notifications.RewireNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Ongoing ForegroundService that keeps Rewire alive in the background (AGENTS.md §15, §20)
 * so Android and aggressive OEM battery killers (ColorOS, MIUI, etc.) do not terminate habit guard.
 * Displays a persistent, low-priority ongoing notification.
 */
class GuardMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watchdogJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as? RewireApp ?: return
        val notifier = app.container.notifier
        notifier.createChannels()

        val notification = notifier.guardOngoingNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                RewireNotifier.Ids.GUARD_ONGOING,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(RewireNotifier.Ids.GUARD_ONGOING, notification)
        }
        startWatchdog()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as? RewireApp
        val hasActiveHabits = app?.container?.habits?.habits?.value?.any { it.habit.enabled } ?: false
        if (!hasActiveHabits) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = serviceScope.launch {
            val app = application as? RewireApp ?: return@launch
            while (isActive) {
                delay(5_000)
                val habits = app.container.habits.habits.value
                val anyActive = habits.any { it.habit.enabled }
                if (!anyActive) {
                    stopSelf()
                    break
                }
            }
        }
    }

    override fun onDestroy() {
        watchdogJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, GuardMonitorService::class.java)
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, GuardMonitorService::class.java))
            }
        }

        fun sync(context: Context, anyHabitEnabled: Boolean) {
            if (anyHabitEnabled) {
                start(context)
            } else {
                stop(context)
            }
        }
    }
}
