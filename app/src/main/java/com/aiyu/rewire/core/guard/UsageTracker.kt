package com.aiyu.rewire.core.guard

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.aiyu.rewire.core.permissions.SystemPermissions
import com.aiyu.rewire.domain.habit.HabitProfile
import com.aiyu.rewire.domain.restriction.RuleEngine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reads usage and screen-time data directly from Android's UsageStatsManager —
 * the exact underlying system data source used by Digital Wellbeing.
 */
open class UsageTracker(private val context: Context? = null) {

    open fun hasPermission(): Boolean = context?.let(SystemPermissions::usageAccessGranted) ?: false

    /**
     * Total foreground minutes today across [packages].
     * Returns null when Usage access isn't granted.
     */
    open fun minutesToday(packages: Set<String>, now: Long = System.currentTimeMillis(), since: Long? = null): Int? {
        if (packages.isEmpty()) return null
        val millis = foregroundMillisToday(now, since) ?: return null
        return (packages.sumOf { millis[it] ?: 0L } / 60_000L).toInt()
    }

    /** Usage the daily limit is judged on: [pkg] alone when given (limits are per app), else every app of the habit. Only the current allowed window when the habit has one, else the whole day. */
    fun limitMinutes(p: HabitProfile, pkg: String? = null, now: Long = System.currentTimeMillis()): Int? {
        val zdt = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
        val from = RuleEngine.limitCountsFromMinutes(p.rule, zdt.hour * 60 + zdt.minute)
        val since = from?.let { zdt.toLocalDate().atStartOfDay(zdt.zone).toInstant().toEpochMilli() + it * 60_000L }
        return minutesToday(pkg?.let { setOf(it) } ?: p.apps.map { it.packageName }.toSet(), now, since)
    }

    /** Single app foreground minutes today. */
    fun appMinutesToday(packageName: String, now: Long = System.currentTimeMillis()): Int =
        minutesToday(setOf(packageName), now) ?: 0

    /**
     * Map of packageName to minutes used today for all applications (matching Digital Wellbeing).
     */
    fun allAppsMinutesToday(now: Long = System.currentTimeMillis()): Map<String, Int> =
        foregroundMillisToday(now).orEmpty().mapNotNull { (pkg, ms) ->
            val minutes = (ms / 60_000L).toInt()
            if (minutes > 0) pkg to minutes else null
        }.toMap()

    /**
     * Total device screen time today in minutes (matching Digital Wellbeing dashboard).
     */
    fun totalScreenTimeToday(now: Long = System.currentTimeMillis()): Int =
        (foregroundMillisToday(now).orEmpty().values.sum() / 60_000L).toInt()

    /**
     * Per-package foreground millis since local midnight, built from raw UsageEvents like Digital
     * Wellbeing. The aggregated UsageStats buckets aren't aligned to midnight and overlap the query
     * range, so they leak yesterday's usage into today.
     */
    private fun foregroundMillisToday(now: Long, since: Long? = null): Map<String, Long>? {
        val ctx = context ?: return null
        if (!SystemPermissions.usageAccessGranted(ctx)) return null
        val usm = ctx.getSystemService(UsageStatsManager::class.java) ?: return null
        val start = since ?: LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return queryMillis(start, now)
    }

    /** Total screen minutes on [date] (to [now] for today); null without Usage access. The system keeps only about a week of events. */
    open fun screenTimeOn(date: LocalDate, now: Long = System.currentTimeMillis()): Int? {
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = minOf(now, date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
        if (end <= start) return null
        return queryMillis(start, end)?.let { (it.values.sum() / 60_000L).toInt() }
    }

    private fun queryMillis(start: Long, now: Long): Map<String, Long>? {
        val ctx = context ?: return null
        val usm = ctx.getSystemService(UsageStatsManager::class.java) ?: return null
        return runCatching {
            val events = usm.queryEvents(start, now)
            val list = ArrayList<Event>()
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                list += Event(e.packageName, e.className, e.eventType, e.timeStamp)
            }
            foregroundMillis(list, start, now)
        }.getOrNull()
    }

    /** App open in the foreground right now per UsageEvents; null without usage access or when unknown. */
    open fun foregroundApp(now: Long = System.currentTimeMillis()): String? {
        val ctx = context ?: return null
        if (!SystemPermissions.usageAccessGranted(ctx)) return null
        val usm = ctx.getSystemService(UsageStatsManager::class.java) ?: return null
        return lastResumed(usm, now)?.first
    }

    /** Last app resumed in the past 30 min (today) and not paused since, with its resume time. */
    private fun lastResumed(usm: UsageStatsManager, now: Long): Pair<String, Long>? {
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
            lastResumedPkg?.let { it to lastResumedTime }
        }.getOrNull()
    }

    /** App resumes recorded in [from, to], oldest first; empty without usage access. Cheap: a seconds-wide window. */
    open fun resumes(from: Long, to: Long): List<Event> {
        val ctx = context ?: return emptyList()
        if (!SystemPermissions.usageAccessGranted(ctx)) return emptyList()
        val usm = ctx.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        return runCatching {
            val events = usm.queryEvents(from, to)
            val list = ArrayList<Event>()
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                list += Event(e.packageName, e.className, e.eventType, e.timeStamp)
            }
            resumes(list)
        }.getOrDefault(emptyList())
    }

    data class Event(val pkg: String?, val cls: String?, val type: Int, val time: Long)

    internal companion object {
        private const val RESUMED = 1 // ACTIVITY_RESUMED
        private const val PAUSED = 2 // ACTIVITY_PAUSED
        private const val SCREEN_OFF = 16 // SCREEN_NON_INTERACTIVE
        private const val STOPPED = 23 // ACTIVITY_STOPPED
        private const val SHUTDOWN = 26 // DEVICE_SHUTDOWN

        /**
         * Every resume, in order. Keeping only the newest lost opens: a protected app followed in the same
         * window by any other resume (a System UI overlay, an in-app helper activity) was never judged.
         */
        fun resumes(events: List<Event>): List<Event> = events.filter { it.type == RESUMED && it.pkg != null }

        /**
         * Sums foreground time per package from [events] in [start, now]. A package counts as
         * foreground while any of its activities is resumed; a pause with no resume today means the
         * app was already open at [start]; sessions still open count up to [now].
         */
        fun foregroundMillis(events: List<Event>, start: Long, now: Long): Map<String, Long> {
            val total = HashMap<String, Long>()
            val open = HashMap<String, MutableSet<String?>>()
            val since = HashMap<String, Long>()
            val seen = HashSet<String>()
            fun add(pkg: String, from: Long, to: Long) { total.merge(pkg, (to - from).coerceAtLeast(0L), Long::plus) }
            fun closeAll(at: Long) {
                since.forEach { (pkg, from) -> add(pkg, from, at) }
                since.clear(); open.clear()
            }
            for (e in events) {
                when (e.type) {
                    SCREEN_OFF, SHUTDOWN -> closeAll(e.time)
                    RESUMED -> {
                        val pkg = e.pkg ?: continue
                        seen += pkg
                        val classes = open.getOrPut(pkg) { HashSet() }
                        if (classes.isEmpty()) since[pkg] = e.time
                        classes += e.cls
                    }
                    PAUSED, STOPPED -> {
                        val pkg = e.pkg ?: continue
                        val classes = open[pkg]
                        if (classes == null || !classes.remove(e.cls)) {
                            if (pkg !in seen) add(pkg, start, e.time)
                        } else if (classes.isEmpty()) {
                            since.remove(pkg)?.let { add(pkg, it, e.time) }
                        }
                        seen += pkg
                    }
                }
            }
            closeAll(now)
            return total
        }
    }
}
