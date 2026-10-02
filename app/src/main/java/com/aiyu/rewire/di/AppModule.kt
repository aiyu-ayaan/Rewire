package com.rewire.app.di

import android.content.Context
import com.rewire.app.R
import com.rewire.app.core.apps.InstalledAppsSource
import com.rewire.app.core.focus.FocusController
import com.rewire.app.core.guard.HabitEngine
import com.rewire.app.core.guard.UsageTracker
import com.rewire.app.core.notifications.FocusDndManager
import com.rewire.app.core.notifications.RewireNotifier
import com.rewire.app.core.settings.Settings
import com.rewire.app.core.settings.SettingsRepository
import com.rewire.app.data.DbWriter
import com.rewire.app.data.EventRepository
import com.rewire.app.data.FocusSessionRepository
import com.rewire.app.data.HabitRepository
import com.rewire.app.data.RoomEventRepository
import com.rewire.app.data.RoomFocusSessionRepository
import com.rewire.app.data.RoomHabitRepository
import com.rewire.app.data.RoomWarningRepository
import com.rewire.app.data.WarningRepository
import com.rewire.app.data.loadNow
import com.rewire.app.data.local.LegacyImport
import com.rewire.app.data.local.RewireDatabase
import com.rewire.app.domain.focus.FocusState
import com.rewire.app.service.focus.FocusTimerService
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
        val defaults = RoomWarningRepository.defaults(context.resources.openRawResource(R.raw.default_warnings).bufferedReader().use { it.readText() })
        return RoomWarningRepository(db.warnings(), writer, defaults)
    }

    @Provides @Singleton
    fun events(db: RewireDatabase, writer: DbWriter): EventRepository = RoomEventRepository(db.events(), writer)

    @Provides @Singleton
    fun focusSessions(db: RewireDatabase, writer: DbWriter): FocusSessionRepository = RoomFocusSessionRepository(db.focusSessions(), writer)

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
