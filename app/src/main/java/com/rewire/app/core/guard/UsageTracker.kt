package com.rewire.app.core.guard

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.rewire.app.core.permissions.SystemPermissions
import java.time.LocalDate
import java.time.ZoneId

/** Foreground minutes today from usage events (includes the session still open right now). */
class UsageTracker(private val context: Context) {

    /** Null when Usage access isn't granted — daily limits then can't be enforced. */
    fun minutesToday(packages: Set<String>, now: Long = System.currentTimeMillis()): Int? {
        if (packages.isEmpty() || !SystemPermissions.usageAccessGranted(context)) return null
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val events = usm.queryEvents(start, now)
        val openedAt = HashMap<String, Long>()
        var total = 0L
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName
            if (pkg !in packages) continue
            @Suppress("DEPRECATION")
            when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> openedAt.putIfAbsent(pkg, e.timeStamp)
                UsageEvents.Event.MOVE_TO_BACKGROUND -> openedAt.remove(pkg)?.let { total += e.timeStamp - it }
            }
        }
        openedAt.values.forEach { total += now - it }
        return (total / 60_000).toInt()
    }
}
