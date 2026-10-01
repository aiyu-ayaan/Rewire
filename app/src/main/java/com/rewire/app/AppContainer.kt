package com.rewire.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rewire.app.core.apps.InstalledAppsSource
import com.rewire.app.core.focus.FocusController
import com.rewire.app.service.focus.FocusTimerService
import com.rewire.app.core.settings.Settings
import com.rewire.app.core.settings.SettingsRepository
import com.rewire.app.core.notifications.RewireNotifier
import com.rewire.app.data.EventRepository
import com.rewire.app.data.HabitRepository
import com.rewire.app.core.guard.HabitEngine
import com.rewire.app.core.guard.UsageTracker
import com.rewire.app.data.DbWriter
import com.rewire.app.data.FocusSessionRepository
import com.rewire.app.data.RoomEventRepository
import com.rewire.app.data.RoomFocusSessionRepository
import com.rewire.app.data.RoomHabitRepository
import com.rewire.app.data.RoomWarningRepository
import com.rewire.app.data.loadNow
import com.rewire.app.data.local.LegacyImport
import com.rewire.app.data.local.RewireDatabase
import com.rewire.app.domain.focus.FocusState
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import com.rewire.app.data.WarningRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

// ponytail: manual DI for Phase 1 (small graph); Hilt in Phase 2.
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database = RewireDatabase.create(context).also { db -> loadNow { LegacyImport.runIfNeeded(context, db) } }
    private val writer = DbWriter(appScope)

    val settingsRepository = SettingsRepository(database.settings())
    val settings: StateFlow<Settings?> = settingsRepository.settings
        .stateIn(appScope, SharingStarted.Eagerly, loadNow { settingsRepository.current() })

    val habits: HabitRepository = RoomHabitRepository(database.habits(), writer)
    private val defaultWarnings = RoomWarningRepository.defaults(
        context.resources.openRawResource(R.raw.default_warnings).bufferedReader().use { it.readText() }
    )
    val warnings: WarningRepository = RoomWarningRepository(database.warnings(), writer, defaultWarnings)
    val events: EventRepository = RoomEventRepository(database.events(), writer)
    val focusSessions: FocusSessionRepository = RoomFocusSessionRepository(database.focusSessions(), writer)

    /** The one focus session; written by [FocusController], read by the Guard engine for bypass rules. */
    val focusState = MutableStateFlow(FocusState())
    val installedApps = InstalledAppsSource(context)
    val usage = UsageTracker(context)

    val engine = HabitEngine(context, habits, events, settings, focusState, usage, MainScope())

    val notifier = RewireNotifier(context) { category -> settings.value?.notifications?.get(category) ?: true }
    val dndManager = com.rewire.app.core.notifications.FocusDndManager(context)

    val focus = FocusController(
        focusState, focusSessions, events, notifier, dndManager, settings, MainScope(),
        keepAlive = { active -> FocusTimerService.sync(context, active) },
    )
}

@Composable
inline fun <reified VM : ViewModel> rewireViewModel(
    key: String? = null,
    owner: ViewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current),
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = (LocalContext.current.applicationContext as RewireApp).container
    return viewModel(viewModelStoreOwner = owner, key = key) { create(container) }
}
