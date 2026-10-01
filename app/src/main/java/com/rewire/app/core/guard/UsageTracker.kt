package com.rewire.app.core.guard

import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import com.rewire.app.core.permissions.SystemPermissions
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reads usage and screen-time data directly from Android's UsageStatsManager —
 * the exact underlying system data source used by Digital Wellbeing.
 */
class UsageTracker(private val context: Context) {

    /**
     * Total foreground minutes today across [packages].
     * Returns null when Usage access isn't granted.
     */
    fun minutesToday(packages: Set<String>, now: Long = System.currentTimeMillis()): Int? {
        if (packages.isEmpty() || !SystemPermissions.usageAccessGranted(context)) return null
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // 1. Query aggregated stats (same API used by Digital Wellbeing)
        val aggregated = runCatching { usm.queryAndAggregateUsageStats(start, now) }.getOrNull().orEmpty()
        var totalMillis = packages.sumOf { aggregated[it]?.totalTimeInForeground ?: 0L }

        // 2. Add currently active un-flushed session from recent UsageEvents
        val activeDelta = activeSessionDuration(usm, packages, now)
        totalMillis += activeDelta

        return (totalMillis / 60_000L).toInt()
    }

    /** Single app foreground minutes today. */
    fun appMinutesToday(packageName: String, now: Long = System.currentTimeMillis()): Int =
        minutesToday(setOf(packageName), now) ?: 0

    /**
     * Map of packageName to minutes used today for all applications (matching Digital Wellbeing).
     */
    fun allAppsMinutesToday(now: Long = System.currentTimeMillis()): Map<String, Int> {
        if (!SystemPermissions.usageAccessGranted(context)) return emptyMap()
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val aggregated = runCatching { usm.queryAndAggregateUsageStats(start, now) }.getOrNull().orEmpty()
        return aggregated.mapNotNull { (pkg, stats) ->
            val minutes = (stats.totalTimeInForeground / 60_000L).toInt()
            if (minutes > 0) pkg to minutes else null
        }.toMap()
    }

    /**
     * Total device screen time today in minutes (matching Digital Wellbeing dashboard).
     */
    fun totalScreenTimeToday(now: Long = System.currentTimeMillis()): Int {
        if (!SystemPermissions.usageAccessGranted(context)) return 0
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return 0
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val statsList = runCatching {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now)
        }.getOrNull().orEmpty()
        val totalMillis = statsList.sumOf { it.totalTimeInForeground }
        return (totalMillis / 60_000L).toInt()
    }

    /**
     * Detects if any app in [packages] is currently open in the foreground right now,
     * calculating any un-flushed duration since its last resumed event.
     */
    private fun activeSessionDuration(usm: UsageStatsManager, packages: Set<String>, now: Long): Long {
        return runCatching {
            val recentStart = (now - 30 * 60_000L).coerceAtLeast(
                LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            )
            val events = usm.queryEvents(recentStart, now)
            val e = UsageEvents.Event()
            var lastResumedPkg: String? = null
            var lastResumedTime = 0L

            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                val type = e.eventType
                if (type == UsageEvents.Event.ACTIVITY_RESUMED || type == 1 /* MOVE_TO_FOREGROUND */) {
                    lastResumedPkg = e.packageName
                    lastResumedTime = e.timeStamp
                } else if (type == UsageEvents.Event.ACTIVITY_PAUSED || type == 2 /* MOVE_TO_BACKGROUND */ ||
                    type == UsageEvents.Event.ACTIVITY_STOPPED || type == 16 /* SCREEN_NON_INTERACTIVE */
                ) {
                    if (e.packageName == lastResumedPkg) {
                        lastResumedPkg = null
                    }
                }
            }

            if (lastResumedPkg != null && lastResumedPkg in packages && lastResumedTime > 0L) {
                (now - lastResumedTime).coerceIn(0L, 30 * 60_000L)
            } else 0L
        }.getOrDefault(0L)
    }
}
