package com.rewire.app.core.guard

import com.rewire.app.core.datastore.FocusBypass
import com.rewire.app.core.datastore.Settings
import com.rewire.app.core.datastore.ThemeMode
import com.rewire.app.core.datastore.UserProfile
import com.rewire.app.data.EventRepository
import com.rewire.app.data.HabitRepository
import com.rewire.app.domain.analytics.HabitEvent
import com.rewire.app.domain.analytics.HabitEventType
import com.rewire.app.domain.focus.FocusSessionStatus
import com.rewire.app.domain.focus.FocusState
import com.rewire.app.domain.habit.Habit
import com.rewire.app.domain.habit.HabitProfile
import com.rewire.app.domain.habit.ProtectedApp
import com.rewire.app.domain.habit.RestrictionRule
import com.rewire.app.domain.habit.WarningLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class HabitEngineOutcomeTest {

    private class FakeHabitRepository(initial: List<HabitProfile> = emptyList()) : HabitRepository {
        private val _habits = MutableStateFlow(initial)
        override val habits: StateFlow<List<HabitProfile>> = _habits
        override fun habit(id: String): Flow<HabitProfile?> = _habits.map { list -> list.find { it.id == id } }
        override fun create(name: String, level: WarningLevel, packages: List<String>): HabitProfile = error("unused")
        override fun update(profile: HabitProfile) {
            _habits.value = _habits.value.map { if (it.id == profile.id) profile else it }
        }
        override fun delete(id: String) {
            _habits.value = _habits.value.filterNot { it.id == id }
        }
        fun setHabits(list: List<HabitProfile>) {
            _habits.value = list
        }
    }

    private class FakeEventRepository : EventRepository {
        private val _events = MutableStateFlow<List<HabitEvent>>(emptyList())
        override val events: StateFlow<List<HabitEvent>> = _events
        val logged = mutableListOf<HabitEvent>()

        override fun log(type: HabitEventType, packageName: String?, habitId: String?, metadata: Map<String, String>) {
            val event = HabitEvent(UUID.randomUUID().toString(), type, packageName, habitId, System.currentTimeMillis(), metadata)
            logged.add(event)
            _events.value = _events.value + event
        }
    }

    private class FakeUsageTracker : UsageTracker() {
        var minutes: Int? = null
        override fun hasPermission(): Boolean = true
        override fun minutesToday(packages: Set<String>, now: Long): Int? = minutes
    }

    private class FakeEnginePlatform : EnginePlatform {
        override val packageName: String = "com.rewire.app"
        val keyboards = setOf("com.google.android.inputmethod.latin")
        var homeSent = false
        val guardShown = mutableListOf<GuardCall>()
        val launchedApps = mutableListOf<String>()
        var guardServiceSynced: Boolean? = null

        data class GuardCall(val pkg: String, val habitId: String, val level: WarningLevel, val blockReason: String?)

        override fun isKeyboard(pkg: String): Boolean = pkg in keyboards

        override fun sendHome(): Boolean {
            homeSent = true
            return true
        }

        override fun showGuard(pkg: String, habitId: String, level: WarningLevel, blockReason: String?) {
            guardShown.add(GuardCall(pkg, habitId, level, blockReason))
        }

        override fun launchApp(pkg: String) {
            launchedApps.add(pkg)
        }

        override fun syncGuardService(anyHabitEnabled: Boolean) {
            guardServiceSynced = anyHabitEnabled
        }
    }

    private lateinit var habitRepo: FakeHabitRepository
    private lateinit var eventRepo: FakeEventRepository
    private lateinit var settingsFlow: MutableStateFlow<Settings?>
    private lateinit var focusFlow: MutableStateFlow<FocusState>
    private lateinit var usageTracker: FakeUsageTracker
    private lateinit var platform: FakeEnginePlatform
    private lateinit var engine: HabitEngine

    private val minorHabit = HabitProfile(
        habit = Habit("h-minor", "Social Media", null, enabled = true),
        apps = listOf(ProtectedApp("com.instagram.android", "h-minor", WarningLevel.MINOR, enabled = true)),
        rule = RestrictionRule("r1", "h-minor", null, null, null, null, WarningLevel.MINOR, 5)
    )

    private val majorHabit = HabitProfile(
        habit = Habit("h-major", "Doom Scrolling", null, enabled = true),
        apps = listOf(ProtectedApp("com.reddit.frontpage", "h-major", WarningLevel.MAJOR, enabled = true)),
        rule = RestrictionRule("r2", "h-major", null, null, null, null, WarningLevel.MAJOR, 5)
    )

    private val maxHabit = HabitProfile(
        habit = Habit("h-max", "Games", null, enabled = true),
        apps = listOf(ProtectedApp("com.supercell.clashroyale", "h-max", WarningLevel.MAX, enabled = true)),
        rule = RestrictionRule("r3", "h-max", null, null, null, null, WarningLevel.MAX, 5)
    )

    private fun testSettings(
        focusBypass: FocusBypass = FocusBypass(minor = false, major = false, max = false)
    ) = Settings(
        onboardingDone = true,
        themeMode = ThemeMode.SYSTEM,
        dynamicColor = false,
        notifications = emptyMap(),
        focusBypass = focusBypass,
        focusDndEnabled = true,
        notificationPermissionAsked = false,
        profile = UserProfile("", null, "", 0),
    )

    @Before
    fun setUp() {
        habitRepo = FakeHabitRepository(listOf(minorHabit, majorHabit, maxHabit))
        eventRepo = FakeEventRepository()
        settingsFlow = MutableStateFlow(testSettings())
        focusFlow = MutableStateFlow(FocusState())
        usageTracker = FakeUsageTracker()
        platform = FakeEnginePlatform()

        engine = HabitEngine(
            habits = habitRepo,
            events = eventRepo,
            settings = settingsFlow,
            focus = focusFlow,
            usage = usageTracker,
            mainScope = CoroutineScope(Dispatchers.Unconfined),
            platform = platform,
        )
    }

    @Test
    fun `syncGuardService is triggered on habit changes`() {
        assertTrue(platform.guardServiceSynced == true)

        habitRepo.setHabits(listOf(minorHabit.copy(habit = minorHabit.habit.copy(enabled = false))))
        assertFalse(platform.guardServiceSynced == true)
    }

    @Test
    fun `ignored packages do not trigger guard`() {
        engine.onForeground("com.rewire.app") // Own package
        engine.onForeground("com.android.systemui") // System UI
        engine.onForeground("com.google.android.inputmethod.latin") // Keyboard

        assertTrue(platform.guardShown.isEmpty())
        assertTrue(eventRepo.logged.isEmpty())
    }

    @Test
    fun `unprotected app allows without guard`() {
        engine.onForeground("com.unprotected.notes")

        assertTrue(platform.guardShown.isEmpty())
        assertTrue(eventRepo.logged.isEmpty())
    }

    @Test
    fun `minor habit triggers minor warning guard and logs WARNING_SHOWN`() {
        engine.onForeground("com.instagram.android")

        assertTrue(platform.homeSent)
        assertEquals(1, platform.guardShown.size)
        val call = platform.guardShown.first()
        assertEquals("com.instagram.android", call.pkg)
        assertEquals("h-minor", call.habitId)
        assertEquals(WarningLevel.MINOR, call.level)
        assertEquals(null, call.blockReason)

        assertEquals(listOf(HabitEventType.WARNING_SHOWN), eventRepo.logged.map { it.type })
        assertEquals("MINOR", eventRepo.logged.first().metadata["level"])
    }

    @Test
    fun `major habit triggers major warning guard and logs WARNING_SHOWN`() {
        engine.onForeground("com.reddit.frontpage")

        assertTrue(platform.homeSent)
        assertEquals(1, platform.guardShown.size)
        val call = platform.guardShown.first()
        assertEquals("com.reddit.frontpage", call.pkg)
        assertEquals("h-major", call.habitId)
        assertEquals(WarningLevel.MAJOR, call.level)
        assertEquals(null, call.blockReason)

        assertEquals(listOf(HabitEventType.WARNING_SHOWN), eventRepo.logged.map { it.type })
        assertEquals("MAJOR", eventRepo.logged.first().metadata["level"])
    }

    @Test
    fun `max habit without boundary triggers block guard with ALWAYS reason`() {
        engine.onForeground("com.supercell.clashroyale")

        assertTrue(platform.homeSent)
        assertEquals(1, platform.guardShown.size)
        val call = platform.guardShown.first()
        assertEquals("com.supercell.clashroyale", call.pkg)
        assertEquals("h-max", call.habitId)
        assertEquals(WarningLevel.MAX, call.level)
        assertEquals("ALWAYS", call.blockReason)

        assertEquals(listOf(HabitEventType.APP_BLOCKED), eventRepo.logged.map { it.type })
        assertEquals("ALWAYS", eventRepo.logged.first().metadata["reason"])
    }

    @Test
    fun `minor habit CONTINUED outcome logs APP_CONTINUED and launches app granting visit`() {
        engine.onForeground("com.instagram.android")
        platform.guardShown.clear()
        eventRepo.logged.clear()

        engine.onGuardResult("com.instagram.android", "h-minor", WarningLevel.MINOR, GuardOutcome.CONTINUED)

        // Event logs: APP_CONTINUED followed by APP_OPENED
        assertEquals(listOf(HabitEventType.APP_CONTINUED, HabitEventType.APP_OPENED), eventRepo.logged.map { it.type })
        assertEquals(listOf("com.instagram.android"), platform.launchedApps)

        // Next foreground for the same app is granted: does not show guard
        platform.guardShown.clear()
        engine.onForeground("com.instagram.android")
        assertTrue(platform.guardShown.isEmpty())
    }

    @Test
    fun `major habit CONTINUED outcome logs OVERRIDE_USED and launches app`() {
        engine.onForeground("com.reddit.frontpage")
        platform.guardShown.clear()
        eventRepo.logged.clear()

        engine.onGuardResult("com.reddit.frontpage", "h-major", WarningLevel.MAJOR, GuardOutcome.CONTINUED)

        assertEquals(listOf(HabitEventType.OVERRIDE_USED, HabitEventType.APP_OPENED), eventRepo.logged.map { it.type })
        assertEquals(listOf("com.reddit.frontpage"), platform.launchedApps)
    }

    @Test
    fun `max habit EMERGENCY_ONCE outcome logs OVERRIDE_USED with emergency metadata and launches app`() {
        engine.onForeground("com.supercell.clashroyale")
        platform.guardShown.clear()
        eventRepo.logged.clear()

        engine.onGuardResult("com.supercell.clashroyale", "h-max", WarningLevel.MAX, GuardOutcome.EMERGENCY_ONCE)

        assertEquals(listOf(HabitEventType.OVERRIDE_USED, HabitEventType.APP_OPENED), eventRepo.logged.map { it.type })
        assertEquals("once", eventRepo.logged.first().metadata["emergency"])
        assertEquals(listOf("com.supercell.clashroyale"), platform.launchedApps)

        // The single visit is granted
        engine.onForeground("com.supercell.clashroyale")
        assertTrue(platform.guardShown.isEmpty())

        // Switching to another app revokes the grant
        engine.onForeground("com.unprotected.notes")

        // Reopening max app now blocks again
        engine.onForeground("com.supercell.clashroyale")
        assertEquals(1, platform.guardShown.size)
        assertEquals("com.supercell.clashroyale", platform.guardShown.first().pkg)
    }

    @Test
    fun `WENT_BACK outcome logs WENT_BACK and does not launch app`() {
        engine.onForeground("com.reddit.frontpage")
        platform.guardShown.clear()
        eventRepo.logged.clear()

        engine.onGuardResult("com.reddit.frontpage", "h-major", WarningLevel.MAJOR, GuardOutcome.WENT_BACK)

        assertEquals(listOf(HabitEventType.WENT_BACK), eventRepo.logged.map { it.type })
        assertTrue(platform.launchedApps.isEmpty())

        // Next open will trigger guard fresh
        engine.onForeground("com.reddit.frontpage")
        assertEquals(1, platform.guardShown.size)
    }

    @Test
    fun `ABANDONED outcome does not log extra event and leaves visit ungranted`() {
        engine.onForeground("com.instagram.android")
        platform.guardShown.clear()
        eventRepo.logged.clear()

        engine.onGuardResult("com.instagram.android", "h-minor", WarningLevel.MINOR, GuardOutcome.ABANDONED)

        assertTrue(eventRepo.logged.isEmpty())
        assertTrue(platform.launchedApps.isEmpty())

        // Next open triggers guard fresh
        engine.onForeground("com.instagram.android")
        assertEquals(1, platform.guardShown.size)
    }

    @Test
    fun `silenceNotificationFrom silences blocked Max apps and logs NOTIFICATION_BLOCKED`() {
        // Clash Royale is Max and blocked
        assertTrue(engine.silenceNotificationFrom("com.supercell.clashroyale"))
        assertEquals(listOf(HabitEventType.NOTIFICATION_BLOCKED), eventRepo.logged.map { it.type })

        // Instagram is Minor: not silenced
        assertFalse(engine.silenceNotificationFrom("com.instagram.android"))

        // Unprotected app: not silenced
        assertFalse(engine.silenceNotificationFrom("com.unprotected.notes"))
    }

    @Test
    fun `silenceNotificationFrom does not silence granted visit`() {
        engine.onGuardResult("com.supercell.clashroyale", "h-max", WarningLevel.MAX, GuardOutcome.EMERGENCY_ONCE)
        eventRepo.logged.clear()

        // Visit is granted: notification should not be silenced
        assertFalse(engine.silenceNotificationFrom("com.supercell.clashroyale"))
        assertTrue(eventRepo.logged.isEmpty())
    }

    @Test
    fun `focus mode bypass allows minor habit without guard when bypassMinor is enabled`() {
        settingsFlow.value = testSettings(focusBypass = FocusBypass(minor = true, major = false, max = false))
        focusFlow.value = FocusState(status = FocusSessionStatus.FOCUSING)

        engine.onForeground("com.instagram.android")

        assertTrue(platform.guardShown.isEmpty())
        assertEquals(listOf(HabitEventType.APP_OPENED), eventRepo.logged.map { it.type })
    }

    @Test
    fun `daily limit reached triggers block with DAILY_LIMIT reason`() {
        val dailyHabit = HabitProfile(
            habit = Habit("h-daily", "Video Streaming", null, enabled = true),
            apps = listOf(ProtectedApp("com.google.android.youtube", "h-daily", WarningLevel.MAX, enabled = true)),
            rule = RestrictionRule("r-daily", "h-daily", dailyLimitMinutes = 30, null, null, null, WarningLevel.MAX, 5)
        )
        habitRepo.setHabits(listOf(dailyHabit))
        usageTracker.minutes = 35 // Exceeded 30 minutes

        engine.onForeground("com.google.android.youtube")

        assertEquals(1, platform.guardShown.size)
        val call = platform.guardShown.first()
        assertEquals("com.google.android.youtube", call.pkg)
        assertEquals(WarningLevel.MAX, call.level)
        assertEquals("DAILY_LIMIT", call.blockReason)

        assertEquals(listOf(HabitEventType.APP_BLOCKED), eventRepo.logged.map { it.type })
        assertEquals("DAILY_LIMIT", eventRepo.logged.first().metadata["reason"])
    }

    @Test
    fun `major habit with launch limit allows up to limit then blocks`() {
        val launchHabit = HabitProfile(
            habit = Habit("h-chrome", "Browsing", null, enabled = true),
            apps = listOf(ProtectedApp("com.android.chrome", "h-chrome", WarningLevel.MAJOR, enabled = true)),
            rule = RestrictionRule("r-chrome", "h-chrome", dailyLimitMinutes = null, allowedStartMinutes = null, allowedEndMinutes = null, maxLaunches = 2, WarningLevel.MAJOR, 6)
        )
        habitRepo.setHabits(listOf(launchHabit))

        // 1st open: triggers MAJOR warning
        engine.onForeground("com.android.chrome")
        assertEquals(1, platform.guardShown.size)
        assertEquals(WarningLevel.MAJOR, platform.guardShown.last().level)
        assertEquals(null, platform.guardShown.last().blockReason)

        // User continues: 1st launch recorded
        engine.onGuardResult("com.android.chrome", "h-chrome", WarningLevel.MAJOR, GuardOutcome.CONTINUED)
        assertEquals(1, eventRepo.events.value.count { it.type == HabitEventType.APP_OPENED })

        // User leaves app
        engine.onForeground("com.android.launcher")

        // 2nd open: triggers MAJOR warning
        engine.onForeground("com.android.chrome")
        assertEquals(2, platform.guardShown.size)
        assertEquals(WarningLevel.MAJOR, platform.guardShown.last().level)
        assertEquals(null, platform.guardShown.last().blockReason)

        // User continues: 2nd launch recorded
        engine.onGuardResult("com.android.chrome", "h-chrome", WarningLevel.MAJOR, GuardOutcome.CONTINUED)
        assertEquals(2, eventRepo.events.value.count { it.type == HabitEventType.APP_OPENED })

        // User leaves app
        engine.onForeground("com.android.launcher")

        // 3rd open (more than 2 times!): now BLOCKED with LAUNCH_LIMIT!
        engine.onForeground("com.android.chrome")
        assertEquals(3, platform.guardShown.size)
        val blockedCall = platform.guardShown.last()
        assertEquals(WarningLevel.MAX, blockedCall.level)
        assertEquals("LAUNCH_LIMIT", blockedCall.blockReason)
        assertEquals(HabitEventType.APP_BLOCKED, eventRepo.logged.last().type)
        assertEquals("LAUNCH_LIMIT", eventRepo.logged.last().metadata["reason"])
    }

    @Test
    fun `returning to app via recents re-triggers guard evaluation`() {
        // Setup: major habit with launch limit
        val launchHabit = HabitProfile(
            habit = Habit("h-chrome", "Browsing", null, enabled = true),
            apps = listOf(ProtectedApp("com.android.chrome", "h-chrome", WarningLevel.MAJOR, enabled = true)),
            rule = RestrictionRule("r-chrome", "h-chrome", null, null, null, maxLaunches = 2, WarningLevel.MAJOR, 5)
        )
        habitRepo.setHabits(listOf(launchHabit))

        // 1st open: warning shown
        engine.onForeground("com.android.chrome")
        assertEquals(1, platform.guardShown.size)

        // User continues
        engine.onGuardResult("com.android.chrome", "h-chrome", WarningLevel.MAJOR, GuardOutcome.CONTINUED)

        // User opens recents (System UI) then taps Chrome again — must re-trigger guard
        engine.onForeground("com.android.systemui")  // recents screen
        engine.onForeground("com.android.chrome")     // tap Chrome from recents
        assertEquals(2, platform.guardShown.size)     // new warning must appear
        assertEquals(WarningLevel.MAJOR, platform.guardShown.last().level)
    }

    @Test
    fun `recents clears granted visit so launch count stays accurate`() {
        val launchHabit = HabitProfile(
            habit = Habit("h-cam", "Camera", null, enabled = true),
            apps = listOf(ProtectedApp("com.android.camera", "h-cam", WarningLevel.MAJOR, enabled = true)),
            rule = RestrictionRule("r-cam", "h-cam", null, null, null, maxLaunches = 2, WarningLevel.MAJOR, 5)
        )
        habitRepo.setHabits(listOf(launchHabit))

        // Open 1: continue through warning
        engine.onForeground("com.android.camera")
        engine.onGuardResult("com.android.camera", "h-cam", WarningLevel.MAJOR, GuardOutcome.CONTINUED)
        assertEquals(1, eventRepo.events.value.count { it.type == HabitEventType.APP_OPENED })

        // Open 2 via recents: continue through warning
        engine.onForeground("com.android.systemui")
        engine.onForeground("com.android.camera")
        engine.onGuardResult("com.android.camera", "h-cam", WarningLevel.MAJOR, GuardOutcome.CONTINUED)
        assertEquals(2, eventRepo.events.value.count { it.type == HabitEventType.APP_OPENED })

        // Open 3 via recents: must be BLOCKED
        engine.onForeground("com.android.systemui")
        engine.onForeground("com.android.camera")
        val last = platform.guardShown.last()
        assertEquals(WarningLevel.MAX, last.level)
        assertEquals("LAUNCH_LIMIT", last.blockReason)
    }

    @Test
    fun `picking the blocked app from recents while block screen is open re-blocks it`() {
        val launchHabit = HabitProfile(
            habit = Habit("h-chrome", "Browsing", null, enabled = true),
            apps = listOf(ProtectedApp("com.android.chrome", "h-chrome", WarningLevel.MAJOR, enabled = true)),
            rule = RestrictionRule("r-chrome", "h-chrome", null, null, null, maxLaunches = 2, WarningLevel.MAJOR, 5)
        )
        habitRepo.setHabits(listOf(launchHabit))
        repeat(2) {
            engine.onForeground("com.android.chrome")
            engine.onGuardResult("com.android.chrome", "h-chrome", WarningLevel.MAJOR, GuardOutcome.CONTINUED)
            engine.onForeground("com.android.chrome") // granted relaunch
            engine.onForeground("com.google.android.apps.nexuslauncher")
        }

        // 3rd open: blocked, engine sends Home and shows the block screen
        engine.onForeground("com.android.chrome")
        assertEquals("LAUNCH_LIMIT", platform.guardShown.last().blockReason)
        val shown = platform.guardShown.size

        // Real device order: Home event, recents (launcher), user taps the Chrome card,
        // and only then does the block screen's onStop report ABANDONED.
        engine.onForeground("com.google.android.apps.nexuslauncher")
        engine.onForeground("com.android.chrome")
        engine.onGuardResult("com.android.chrome", "h-chrome", WarningLevel.MAX, GuardOutcome.ABANDONED)

        assertEquals(shown + 1, platform.guardShown.size)
        assertEquals("LAUNCH_LIMIT", platform.guardShown.last().blockReason)
        assertEquals(2, eventRepo.events.value.count { it.type == HabitEventType.APP_OPENED })
    }

    @Test
    fun `leaving block screen for home does not re-show it`() {
        engine.onForeground("com.supercell.clashroyale")
        engine.onForeground("com.google.android.apps.nexuslauncher")
        engine.onGuardResult("com.supercell.clashroyale", "h-max", WarningLevel.MAX, GuardOutcome.ABANDONED)

        assertEquals(1, platform.guardShown.size)
    }

    @Test
    fun `granted visit is revoked after System UI appears`() {
        // Minor habit — first open is a warn, continue grants a visit
        engine.onForeground("com.instagram.android")
        engine.onGuardResult("com.instagram.android", "h-minor", WarningLevel.MINOR, GuardOutcome.CONTINUED)
        platform.guardShown.clear()

        // The relaunch goes through via granted
        engine.onForeground("com.instagram.android")
        assertTrue(platform.guardShown.isEmpty()) // still granted

        // Now open recents — grant should be revoked
        engine.onForeground("com.android.systemui")
        engine.onForeground("com.instagram.android")
        // A new warning must appear because grant was revoked
        assertEquals(1, platform.guardShown.size)
        assertEquals(WarningLevel.MINOR, platform.guardShown.last().level)
    }
}
