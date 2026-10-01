package com.rewire.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rewire.app.core.apps.InstalledAppsSource
import com.rewire.app.core.datastore.Settings
import com.rewire.app.core.datastore.SettingsRepository
import com.rewire.app.core.notifications.RewireNotifier
import com.rewire.app.data.EventRepository
import com.rewire.app.data.HabitRepository
import com.rewire.app.core.guard.HabitEngine
import com.rewire.app.core.guard.UsageTracker
import com.rewire.app.data.JsonStore
import com.rewire.app.data.PersistentEventRepository
import com.rewire.app.data.PersistentHabitRepository
import com.rewire.app.data.PersistentWarningRepository
import com.rewire.app.domain.analytics.HabitEvent
import com.rewire.app.domain.focus.FocusState
import com.rewire.app.domain.habit.HabitProfile
import com.rewire.app.domain.warning.Warning
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
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

    val settingsRepository = SettingsRepository(context)
    val settings: StateFlow<Settings?> = settingsRepository.settings.stateIn(appScope, SharingStarted.Eagerly, null)

    private val files = context.filesDir
    val habits: HabitRepository = PersistentHabitRepository(
        JsonStore(File(files, "habits.json"), ListSerializer(HabitProfile.serializer()), appScope) { emptyList() }
    )
    private val defaultWarnings = PersistentWarningRepository.defaults(
        context.resources.openRawResource(R.raw.default_warnings).bufferedReader().use { it.readText() }
    )
    val warnings: WarningRepository = PersistentWarningRepository(
        JsonStore(File(files, "warnings.json"), ListSerializer(Warning.serializer()), appScope) { defaultWarnings },
        defaultWarnings,
    )
    val events: EventRepository = PersistentEventRepository(
        JsonStore(File(files, "events.json"), ListSerializer(HabitEvent.serializer()), appScope) { emptyList() }
    )

    /** The one focus session; written by FocusViewModel, read by the Guard engine for bypass rules. */
    val focusState = MutableStateFlow(FocusState())
    val installedApps = InstalledAppsSource(context)
    val usage = UsageTracker(context)

    val engine = HabitEngine(context, habits, events, settings, focusState, usage, MainScope())

    val notifier = RewireNotifier(context) { category -> settings.value?.notifications?.get(category) ?: true }
    val dndManager = com.rewire.app.core.notifications.FocusDndManager(context)
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
