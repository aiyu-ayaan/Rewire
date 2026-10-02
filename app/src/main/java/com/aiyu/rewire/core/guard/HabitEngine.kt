package com.aiyu.rewire.core.guard

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.inputmethod.InputMethodManager
import com.aiyu.rewire.BuildConfig
import com.aiyu.rewire.core.settings.Settings
import com.aiyu.rewire.data.EventRepository
import com.aiyu.rewire.data.HabitRepository
import com.aiyu.rewire.domain.analytics.HabitEventType
import com.aiyu.rewire.domain.focus.FocusSessionStatus
import com.aiyu.rewire.domain.focus.FocusState
import com.aiyu.rewire.domain.habit.HabitProfile
import com.aiyu.rewire.domain.habit.WarningLevel
import com.aiyu.rewire.domain.restriction.RestrictionDecision
import com.aiyu.rewire.domain.restriction.RuleEngine
import com.aiyu.rewire.domain.restriction.RuleInput
import com.aiyu.rewire.feature.guard.GuardActivity
import com.aiyu.rewire.service.accessibility.RewireAccessibilityService
import com.aiyu.rewire.service.monitoring.GuardMonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class GuardOutcome { CONTINUED, EMERGENCY_ONCE, WENT_BACK, ABANDONED }

interface EnginePlatform {
    val packageName: String
    fun isKeyboard(pkg: String): Boolean
    fun showGuard(pkg: String, habitId: String, level: WarningLevel, blockReason: String?)
    /** A guard screen exists right now (created, not yet destroyed). */
    fun isGuardOpen(): Boolean
    fun launchApp(pkg: String)
    fun syncGuardService(anyHabitEnabled: Boolean)
}

class DefaultEnginePlatform(private val context: Context) : EnginePlatform {
    override val packageName: String
        get() = context.packageName

    override fun isKeyboard(pkg: String): Boolean =
        context.getSystemService(InputMethodManager::class.java)?.enabledInputMethodList?.map { it.packageName }?.contains(pkg) == true

    /**
     * Home and the guard in one call, so the guard always lands on top of Home. A separate global HOME
     * action raced the guard: when Home won, the guard task (excluded from recents) sat hidden behind it
     * and the system trimmed it before onCreate, leaving the engine stuck "showing" a screen nobody saw.
     */
    override fun showGuard(pkg: String, habitId: String, level: WarningLevel, blockReason: String?) {
        val start = {
            runCatching {
                context.startActivities(
                    arrayOf(
                        home(),
                        GuardActivity.intent(context, pkg, habitId, level, blockReason)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    )
                )
            }
            Unit
        }
        // Accessibility services may start activities; without one (Lite, or switched off) the start can be refused.
        if (RewireAccessibilityService.isRunning.value || !GuardShield.cover(context, start, goHome = { runCatching { context.startActivity(home()) } })) start()
    }

    private fun home() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    override fun isGuardOpen(): Boolean = GuardActivity.instances > 0

    override fun launchApp(pkg: String) {
        context.packageManager.getLaunchIntentForPackage(pkg)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            ?.let(context::startActivity)
    }

    override fun syncGuardService(anyHabitEnabled: Boolean) {
        GuardMonitorService.sync(context, anyHabitEnabled)
    }
}

/**
 * Orchestrates Guard: foreground app in -> RuleEngine decision -> warning/block screen -> event log.
 * Main-thread only (accessibility callbacks + activity results), so no locking.
 * Business rules live in [RuleEngine]; this class only gathers inputs and acts on decisions.
 *
 * Flow on a warning/block: show the guard screen over Home (Home started underneath it).
 * The protected app can't resume on top of the screen, and leaving the screen never re-triggers
 * by itself (no loops). Continue / emergency relaunch the app and let that one visit through.
 */
class HabitEngine(
    private val habits: HabitRepository,
    private val events: EventRepository,
    private val settings: StateFlow<Settings?>,
    private val focus: StateFlow<FocusState>,
    private val usage: UsageTracker,
    private val mainScope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val platform: EnginePlatform,
) {
    constructor(
        context: Context,
        habits: HabitRepository,
        events: EventRepository,
        settings: StateFlow<Settings?>,
        focus: StateFlow<FocusState>,
        usage: UsageTracker,
        mainScope: CoroutineScope,
        clock: () -> Long = System::currentTimeMillis,
    ) : this(
        habits = habits,
        events = events,
        settings = settings,
        focus = focus,
        usage = usage,
        mainScope = mainScope,
        clock = clock,
        platform = DefaultEnginePlatform(context),
    )

    /** Last real foreground app (Rewire, System UI and keyboards ignored). */
    private var current: String? = null
    /** Guard screen currently shown for this package. */
    private var showingFor: String? = null
    private var shownAt = 0L
    /** One visit let through (Continue / emergency); cleared as soon as the user is in another app. */
    private var granted: String? = null
    /** Protected app brought up while a guard screen was open (e.g. tapped in recents); judged once the screen closes. */
    private var pending: String? = null
    private var recheck: Job? = null
    /** Screen was off since [current] was judged: its next report is re-judged. */
    private var stale = false

    init {
        mainScope.launch {
            habits.habits.collect { list ->
                platform.syncGuardService(list.any { it.habit.enabled })
            }
        }
    }

    /** [className] = window class from the event; only used to tell System UI recents from its overlays. */
    fun onForeground(pkg: String, className: String? = null) {
        if (pkg == SYSTEM_UI) {
            // Recents in System UI (older/AOSP-style devices) means the user left the app: judge the return fresh.
            // Shade, volume panel, biometric prompt are overlays on the same visit: ignore them.
            if (className?.contains("recents", ignoreCase = true) == true) {
                current = null
                granted = null
                recheck?.cancel()
            }
            return
        }
        if (isIgnored(pkg)) return
        if (pkg == current) {
            if (stale) {
                stale = false
                if (showingFor == null && pkg != granted) evaluate(pkg, isRecheck = true)
            }
            return
        }
        stale = false
        current = pkg
        recheck?.cancel()
        if (granted != null && pkg != granted) granted = null
        if (pkg == granted) { profileFor(pkg)?.let { scheduleRecheck(pkg, it) }; return }
        // The system can drop the guard before it reports back (trimmed task, process death before onCreate).
        // Never let a screen that no longer exists keep swallowing opens.
        if (showingFor != null && !platform.isGuardOpen() && clock() - shownAt > GUARD_START_GRACE_MS) showingFor = null
        if (showingFor != null) {
            // The guard's onStop (ABANDONED) lands after this event on real devices; dropping it let the app through.
            pending = pkg.takeIf { profileFor(it) != null }
            return
        }
        evaluate(pkg, isRecheck = false)
    }

    /**
     * Monitoring (re)connected, e.g. the system restarting the service after process death (with back-off,
     * seconds to minutes). Apps opened in that gap sent no event: judge whatever is open now.
     */
    fun resync() {
        usage.foregroundApp(clock())?.let(::onForeground)
    }

    /** (Re)start the monitor service if a habit is on; safe to call often. */
    fun ensureMonitoring() {
        platform.syncGuardService(habits.habits.value.any { it.habit.enabled })
    }

    /**
     * Screen went off. Unlocking back into the same app sends no new app change, so the next report of it
     * is judged again (as a re-check: no extra launch counted). A boundary crossed while the screen was off
     * (allowed window ended, a new day) would otherwise leave the app open until the user switched away.
     */
    fun onScreenOff() {
        if (current != null) stale = true
        recheck?.cancel()
    }

    fun onGuardResult(pkg: String, habitId: String, level: WarningLevel, outcome: GuardOutcome) {
        showingFor = null
        current = null
        val next = pending
        pending = null
        when (outcome) {
            GuardOutcome.CONTINUED -> {
                events.log(if (level == WarningLevel.MINOR) HabitEventType.APP_CONTINUED else HabitEventType.OVERRIDE_USED, pkg, habitId)
                letThrough(pkg, habitId)
            }
            GuardOutcome.EMERGENCY_ONCE -> {
                events.log(HabitEventType.OVERRIDE_USED, pkg, habitId, mapOf("emergency" to "once"))
                letThrough(pkg, habitId)
            }
            GuardOutcome.WENT_BACK -> events.log(HabitEventType.WENT_BACK, pkg, habitId)
            // User went elsewhere; if that was a protected app, judge it now (bounded: one real event, one check).
            // Posted: the guard reports from onStop and finishes right after; re-showing synchronously would
            // hand the new request to that finishing screen and lose it.
            GuardOutcome.ABANDONED -> next?.let { mainScope.launch { onForeground(it) } }
        }
    }

    private fun letThrough(pkg: String, habitId: String) {
        events.log(HabitEventType.APP_OPENED, pkg, habitId)
        granted = pkg
        current = null // the relaunch below must reach onForeground
        platform.launchApp(pkg)
    }

    /**
     * True when [pkg] belongs to a Max habit that would block it right now (and it isn't the visit
     * the user just let through). Pure check: logs only the silenced notification, no launch events.
     */
    fun silenceNotificationFrom(pkg: String): Boolean {
        if (pkg == granted) return false
        val profile = profileFor(pkg) ?: return false
        val blocked = RuleEngine.decide(inputFor(profile)) is RestrictionDecision.Block
        if (blocked) events.log(HabitEventType.NOTIFICATION_BLOCKED, pkg, profile.id)
        return blocked
    }

    private fun evaluate(pkg: String, isRecheck: Boolean) {
        val profile = profileFor(pkg) ?: return
        val d = RuleEngine.decide(inputFor(profile))
        if (BuildConfig.DEBUG) Log.d(TAG, "decision=$d recheck=$isRecheck") // no package names in logs
        when (d) {
            RestrictionDecision.Allow -> {
                if (!isRecheck) events.log(HabitEventType.APP_OPENED, pkg, profile.id)
                scheduleRecheck(pkg, profile)
            }
            is RestrictionDecision.Warn -> {
                events.log(HabitEventType.WARNING_SHOWN, pkg, profile.id, mapOf("level" to d.level.name))
                show(pkg, profile, d.level, null)
            }
            is RestrictionDecision.Block -> {
                events.log(HabitEventType.APP_BLOCKED, pkg, profile.id, mapOf("reason" to d.reason.name))
                show(pkg, profile, WarningLevel.MAX, d.reason.name)
            }
        }
    }

    private fun show(pkg: String, profile: HabitProfile, level: WarningLevel, blockReason: String?) {
        showingFor = pkg
        shownAt = clock()
        current = null // the guard is in front now; any return to an app is a new open
        platform.showGuard(pkg, profile.id, level, blockReason)
    }

    /** Re-check exactly when a Max boundary (window end, daily limit) is crossed — no polling. */
    private fun scheduleRecheck(pkg: String, profile: HabitProfile) {
        recheck?.cancel()
        if (pkg == granted) return // a granted visit runs until the user leaves
        val minutes = RuleEngine.minutesUntilNextBoundary(inputFor(profile)) ?: return
        recheck = mainScope.launch {
            delay(minutes * MINUTE + 1_000)
            if (current == pkg && showingFor == null) evaluate(pkg, isRecheck = true)
        }
    }

    private fun profileFor(pkg: String): HabitProfile? =
        habits.habits.value.firstOrNull { p -> p.habit.enabled && p.apps.any { it.enabled && it.packageName == pkg } }

    private fun inputFor(p: HabitProfile): RuleInput {
        val now = clock()
        val zone = ZoneId.systemDefault()
        val zdt = Instant.ofEpochMilli(now).atZone(zone)
        val today = zdt.toLocalDate()
        val s = settings.value
        val launches = events.events.value.count {
            it.type == HabitEventType.APP_OPENED && it.habitId == p.id && Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() == today
        }
        // Usage is only needed (and only queried) when a daily limit exists.
        val minutes = if (p.rule.dailyLimitMinutes != null) usage.minutesToday(p.apps.map { it.packageName }.toSet(), now) else null
        val time = zdt.toLocalTime()
        return RuleInput(
            profile = p,
            nowMinutes = time.hour * 60 + time.minute,
            launchesToday = launches,
            usageMinutesToday = minutes,
            focusing = focus.value.status == FocusSessionStatus.FOCUSING,
            bypassMinor = s?.focusBypass?.minor ?: false,
            bypassMajor = s?.focusBypass?.major ?: false,
            bypassMax = s?.focusBypass?.max ?: false,
        )
    }

    private fun isIgnored(pkg: String): Boolean =
        pkg == platform.packageName || pkg == SYSTEM_UI || platform.isKeyboard(pkg)

    private companion object {
        const val TAG = "RewireGuard"
        const val MINUTE = 60_000L
        const val GUARD_START_GRACE_MS = 3_000L
        const val SYSTEM_UI = "com.android.systemui"
    }
}
