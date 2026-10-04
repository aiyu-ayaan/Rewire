package com.aiyu.rewire.di

import android.content.Context
import com.aiyu.rewire.R
import com.aiyu.rewire.core.apps.InstalledAppsSource
import com.aiyu.rewire.core.focus.FocusController
import com.aiyu.rewire.core.guard.HabitEngine
import com.aiyu.rewire.core.guard.UsageTracker
import com.aiyu.rewire.core.notifications.FocusDndManager
import com.aiyu.rewire.core.notifications.RewireNotifier
import com.aiyu.rewire.core.settings.Settings
import com.aiyu.rewire.core.settings.SettingsRepository
import com.aiyu.rewire.data.DbWriter
import com.aiyu.rewire.data.BackupRepository
import com.aiyu.rewire.data.DataStoreFocusPresetRepository
import com.aiyu.rewire.data.DataStoreGoalsRepository
import com.aiyu.rewire.data.GoalsRepository
import com.aiyu.rewire.data.EventRepository
import com.aiyu.rewire.data.FocusPresetRepository
import com.aiyu.rewire.data.FocusSessionRepository
import com.aiyu.rewire.data.HabitRepository
import com.aiyu.rewire.data.RoomEventRepository
import com.aiyu.rewire.data.RoomFocusSessionRepository
import com.aiyu.rewire.data.RoomHabitRepository
import com.aiyu.rewire.data.RoomWarningRepository
import com.aiyu.rewire.data.WarningRepository
import com.aiyu.rewire.data.loadNow
import com.aiyu.rewire.data.local.LegacyImport
import com.aiyu.rewire.data.local.RewireDatabase
import com.aiyu.rewire.domain.focus.FocusState
import com.aiyu.rewire.service.focus.FocusTimerService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Qualifier
import javax.inject.Singleton

/** Background work that outlives every screen (DB writes, settings flow). */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class AppScope

/** Main-thread scope for the engine and focus timer (accessibility + UI callbacks are main-thread). */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class MainAppScope

/**
 * The app graph. Everything is a singleton: the accessibility service, foreground services and UI
 * must all see the same rules, settings and focus session.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton @AppScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides @Singleton @MainAppScope
    fun mainScope(): CoroutineScope = MainScope()

    // ---- Database --------------------------------------------------------------------------------

    @Provides @Singleton
    fun database(@ApplicationContext context: Context): RewireDatabase =
        RewireDatabase.create(context).also { db -> loadNow { LegacyImport.runIfNeeded(context, db) } }

    @Provides @Singleton
    fun dbWriter(@AppScope scope: CoroutineScope) = DbWriter(scope)

    @Provides @Singleton
    fun settingsRepository(db: RewireDatabase) = SettingsRepository(db.settings())

    /** Read once synchronously so services judge with real settings from the first event. */
    @Provides @Singleton
    fun settings(repo: SettingsRepository, @AppScope scope: CoroutineScope): StateFlow<Settings?> =
        repo.settings.stateIn(scope, SharingStarted.Eagerly, loadNow { repo.current() })

    @Provides @Singleton
    fun habits(db: RewireDatabase, writer: DbWriter): HabitRepository = RoomHabitRepository(db.habits(), writer)

    @Provides @Singleton
    fun warnings(@ApplicationContext context: Context, db: RewireDatabase, writer: DbWriter): WarningRepository {
        fun read(c: Context) = RoomWarningRepository.defaults(c.resources.openRawResource(R.raw.default_warnings).bufferedReader().use { it.readText() })
        // Room keeps the English baseline; the current language's file (res/raw-xx) only changes how an unedited built-in reads.
        val english = read(context.createConfigurationContext(android.content.res.Configuration().apply { setLocale(java.util.Locale.ENGLISH) }))
        return RoomWarningRepository(db.warnings(), writer, english) { read(com.aiyu.rewire.core.settings.AppLocale.wrap(context)) }
    }

    @Provides @Singleton
    fun events(db: RewireDatabase, writer: DbWriter): EventRepository = RoomEventRepository(db.events(), writer)

    @Provides @Singleton
    fun focusSessions(db: RewireDatabase, writer: DbWriter): FocusSessionRepository = RoomFocusSessionRepository(db.focusSessions(), writer)

    @Provides @Singleton
    fun focusPresets(@ApplicationContext context: Context): FocusPresetRepository = DataStoreFocusPresetRepository(context)

    @Provides @Singleton
    fun quit(@ApplicationContext context: Context): com.aiyu.rewire.data.QuitRepository = com.aiyu.rewire.data.DataStoreQuitRepository(context)

    @Provides @Singleton
    fun screenTimeStore(@ApplicationContext context: Context) = com.aiyu.rewire.data.ScreenTimeStore(context)

    @Provides @Singleton
    fun goalsRepository(@ApplicationContext context: Context): GoalsRepository = DataStoreGoalsRepository(context)

    @Provides @Singleton
    fun backup(db: RewireDatabase, habits: HabitRepository, warnings: WarningRepository, events: EventRepository, presets: FocusPresetRepository, goals: GoalsRepository, screenTime: com.aiyu.rewire.data.ScreenTimeStore) =
        BackupRepository(db, habits, warnings, events, presets, goals, screenTime)

    // ---- Platform --------------------------------------------------------------------------------

    @Provides @Singleton
    fun installedApps(@ApplicationContext context: Context) = InstalledAppsSource(context)

    @Provides @Singleton
    fun usage(@ApplicationContext context: Context) = UsageTracker(context)

    @Provides @Singleton
    fun notifier(@ApplicationContext context: Context, settings: StateFlow<Settings?>) =
        RewireNotifier(context) { category -> settings.value?.notifications?.get(category) ?: true }

    @Provides @Singleton
    fun dnd(@ApplicationContext context: Context) = FocusDndManager(context)

    // ---- Engines ---------------------------------------------------------------------------------

    /** The one focus session; written by [FocusController], read by the Guard engine for bypass rules. */
    @Provides @Singleton
    fun focusState() = MutableStateFlow(FocusState())

    @Provides @Singleton
    fun engine(
        @ApplicationContext context: Context,
        habits: HabitRepository,
        events: EventRepository,
        settings: StateFlow<Settings?>,
        focusState: MutableStateFlow<FocusState>,
        usage: UsageTracker,
        @MainAppScope scope: CoroutineScope,
    ) = HabitEngine(context, habits, events, settings, focusState, usage, scope)

    @Provides @Singleton
    fun focus(
        @ApplicationContext context: Context,
        focusState: MutableStateFlow<FocusState>,
        sessions: FocusSessionRepository,
        events: EventRepository,
        notifier: RewireNotifier,
        dnd: FocusDndManager,
        settings: StateFlow<Settings?>,
        @MainAppScope scope: CoroutineScope,
    ) = FocusController(
        focusState, sessions, events, notifier, dnd, settings, scope,
        keepAlive = { active -> FocusTimerService.sync(context, active) },
    )
}
