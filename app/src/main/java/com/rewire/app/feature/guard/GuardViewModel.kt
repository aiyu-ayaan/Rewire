package com.rewire.app.feature.guard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rewire.app.core.apps.InstalledAppsSource
import com.rewire.app.core.guard.UsageTracker
import com.rewire.app.data.EventRepository
import com.rewire.app.data.HabitRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.rewire.app.domain.analytics.DailyMetrics
import com.rewire.app.domain.analytics.MetricsCalculator
import com.rewire.app.domain.habit.HabitProfile
import com.rewire.app.domain.habit.WarningLevel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

@HiltViewModel
class GuardViewModel @Inject constructor(
    private val habitRepo: HabitRepository,
    events: EventRepository,
    private val usage: UsageTracker,
) : ViewModel() {
    val habits: StateFlow<List<HabitProfile>> = habitRepo.habits

    val today: StateFlow<DailyMetrics> = events.events
        .map {
            MetricsCalculator.daily(it, LocalDate.now(), ZoneId.systemDefault())
                .copy(screenTimeMinutes = usage.totalScreenTimeToday())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MetricsCalculator.daily(emptyList(), LocalDate.now(), ZoneId.systemDefault()))

    fun usageMinutesFor(profile: HabitProfile): Int? =
        usage.minutesToday(profile.apps.map { it.packageName }.toSet())

    fun create(name: String, level: WarningLevel, packages: List<String>) = habitRepo.create(name, level, packages)

    fun setEnabled(profile: HabitProfile, enabled: Boolean) =
        habitRepo.update(profile.copy(habit = profile.habit.copy(enabled = enabled)))
}

@HiltViewModel(assistedFactory = HabitDetailViewModel.Factory::class)
class HabitDetailViewModel @AssistedInject constructor(
    private val habitRepo: HabitRepository,
    private val usage: UsageTracker,
    private val installedApps: InstalledAppsSource,
    @Assisted id: String,
) : ViewModel() {
    @AssistedFactory
    interface Factory { fun create(id: String): HabitDetailViewModel }

    val habit: StateFlow<HabitProfile?> = habitRepo.habit(id).stateIn(viewModelScope, SharingStarted.Eagerly, habitRepo.habits.value.find { it.id == id })

    fun usageMinutesToday(): Int? =
        habit.value?.let { p -> usage.minutesToday(p.apps.map { it.packageName }.toSet()) }

    val hasUsageAccess: Boolean get() = usage.hasPermission()

    fun appLabel(pkg: String) = installedApps.label(pkg)

    private fun edit(block: (HabitProfile) -> HabitProfile) { habit.value?.let { habitRepo.update(block(it)) } }

    fun setEnabled(on: Boolean) = edit { it.copy(habit = it.habit.copy(enabled = on)) }
    fun setLevel(level: WarningLevel) = edit { it.copy(rule = it.rule.copy(warningLevel = level)) }
    fun setDailyLimit(minutes: Int?) = edit { it.copy(rule = it.rule.copy(dailyLimitMinutes = minutes)) }
    fun setMaxLaunches(count: Int?) = edit { it.copy(rule = it.rule.copy(maxLaunches = count)) }
    fun setPause(seconds: Int) = edit { it.copy(rule = it.rule.copy(pauseSeconds = seconds)) }
    fun setWindow(start: Int?, end: Int?) = edit { it.copy(rule = it.rule.copy(allowedStartMinutes = start, allowedEndMinutes = end)) }
    fun removeApp(pkg: String) = edit { p -> p.copy(apps = p.apps.filterNot { it.packageName == pkg }) }
    fun setApps(packages: List<String>) = edit { p ->
        val existing = p.apps.associateBy { it.packageName }
        p.copy(apps = packages.map { existing[it] ?: com.rewire.app.domain.habit.ProtectedApp(it, p.id, p.level, true) })
    }
    fun delete() { habit.value?.let { habitRepo.delete(it.id) } }
}
