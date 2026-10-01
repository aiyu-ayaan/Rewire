package com.rewire.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rewire.app.core.apps.InstalledAppsSource
import com.rewire.app.core.datastore.Settings
import com.rewire.app.core.datastore.SettingsRepository
import com.rewire.app.core.notifications.RewireNotifier
import com.rewire.app.data.EventRepository
import com.rewire.app.data.HabitRepository
import com.rewire.app.data.InMemoryEventRepository
import com.rewire.app.data.InMemoryHabitRepository
import com.rewire.app.data.InMemoryWarningRepository
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

    val habits: HabitRepository = InMemoryHabitRepository()
    val warnings: WarningRepository = InMemoryWarningRepository(
        context.resources.openRawResource(R.raw.default_warnings).bufferedReader().use { it.readText() }
    )
    val events: EventRepository = InMemoryEventRepository()
    val installedApps = InstalledAppsSource(context)

    val notifier = RewireNotifier(context) { category -> settings.value?.notifications?.get(category) ?: true }
}

@Composable
inline fun <reified VM : ViewModel> rewireViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = (LocalContext.current.applicationContext as RewireApp).container
    return viewModel(key = key) { create(container) }
}
