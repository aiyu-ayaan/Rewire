package com.rewire.app.service.monitoring

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import android.os.PowerManager
import com.rewire.app.core.guard.HabitEngine
import com.rewire.app.core.guard.UsageTracker
import com.rewire.app.core.permissions.SystemPermissions
import com.rewire.app.data.HabitRepository
import com.rewire.app.service.accessibility.RewireAccessibilityService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.rewire.app.core.notifications.RewireNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ongoing ForegroundService that keeps Rewire alive in the background (AGENTS.md §15, §20)
 * so Android and aggressive OEM battery killers (ColorOS, MIUI, etc.) do not terminate habit guard.
 * Displays a persistent, low-priority ongoing notification.
 */
@AndroidEntryPoint
class GuardMonitorService : Service() {
    @Inject lateinit var notifier: RewireNotifier
    @Inject lateinit var habits: HabitRepository
    @Inject lateinit var engine: HabitEngine
    @Inject lateinit var usage: UsageTracker

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watchdogJob: Job? = null
    private var usageWatchJob: Job? = null

    override fun onCreate() {
        super.onCreate()
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
        startUsageWatch()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val hasActiveHabits = habits.habits.value.any { it.habit.enabled }
        if (!hasActiveHabits) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = serviceScope.launch {
            while (isActive) {
                delay(5_000)
                val anyActive = habits.habits.value.any { it.habit.enabled }
                if (!anyActive) {
                    stopSelf()
                    break
                }
            }
        }
    }

    /**
     * Detection without Accessibility (Lite build, or Accessibility switched off so payment apps run).
     * Usage events have no push API, so this reads the last second of them, only while the screen is on
     * and Accessibility isn't already reporting. Each event is consumed once (cursor = its timestamp).
     */
    private fun startUsageWatch() {
        usageWatchJob?.cancel()
        usageWatchJob = serviceScope.launch {
            val power = getSystemService(PowerManager::class.java)
            var cursor = 0L // 0 = not watching; the next active tick resyncs instead of reading history
            while (isActive) {
                delay(USAGE_POLL_MS)
                val now = System.currentTimeMillis()
                val active = !RewireAccessibilityService.isRunning.value && power.isInteractive &&
                    SystemPermissions.usageFallbackReady(this@GuardMonitorService)
                if (!active) { cursor = 0L; continue }
                if (cursor == 0L) {
                    cursor = now
                    withContext(Dispatchers.Main) { engine.resync() }
                    continue
                }
                // Events can land a moment after they happen: look back a little, never before the last one used.
                val e = usage.latestResume(maxOf(cursor + 1, now - USAGE_LOOKBACK_MS), now) ?: continue
                cursor = e.time
                withContext(Dispatchers.Main) { engine.onForeground(e.pkg ?: return@withContext, e.cls) }
            }
        }
    }

    override fun onDestroy() {
        usageWatchJob?.cancel()
        watchdogJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val USAGE_POLL_MS = 1_000L
        private const val USAGE_LOOKBACK_MS = 5_000L

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
