package com.aiyu.rewire.feature.guard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiyu.rewire.core.apps.InstalledAppsSource
import com.aiyu.rewire.core.guard.UsageTracker
import com.aiyu.rewire.data.EventRepository
import com.aiyu.rewire.data.GoalsRepository
import com.aiyu.rewire.data.HabitRepository
import com.aiyu.rewire.domain.goals.DayProgress
import com.aiyu.rewire.domain.goals.Streaks
import kotlinx.coroutines.flow.combine
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.aiyu.rewire.domain.analytics.DailyMetrics
import com.aiyu.rewire.domain.analytics.MetricsCalculator
import com.aiyu.rewire.domain.habit.AppLimits
import com.aiyu.rewire.domain.habit.HabitProfile
import com.aiyu.rewire.domain.habit.RestrictionRule
import com.aiyu.rewire.domain.habit.WarningLevel
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
    goals: GoalsRepository,
) : ViewModel() {
    val habits: StateFlow<List<HabitProfile>> = habitRepo.habits

    val today: StateFlow<DailyMetrics> = events.events
        .map {
            MetricsCalculator.daily(it, LocalDate.now(), ZoneId.systemDefault())
                .copy(screenTimeMinutes = usage.totalScreenTimeToday())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MetricsCalculator.daily(emptyList(), LocalDate.now(), ZoneId.systemDefault()))

    val progress: StateFlow<DayProgress> = combine(events.events, goals.goals) { e, g ->
        Streaks.progress(e, LocalDate.now(), ZoneId.systemDefault(), g)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayProgress.EMPTY)

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

    fun appUsageMinutesToday(pkg: String): Int? = usage.minutesToday(setOf(pkg))

    val hasUsageAccess: Boolean get() = usage.hasPermission()

    fun appLabel(pkg: String) = installedApps.label(pkg)

    private fun edit(block: (HabitProfile) -> HabitProfile) { habit.value?.let { habitRepo.update(block(it)) } }

    fun setEnabled(on: Boolean) = edit { it.copy(habit = it.habit.copy(enabled = on)) }
    fun setLevel(level: WarningLevel) = edit { it.copy(rule = it.rule.copy(warningLevel = level)) }
    fun setDailyLimit(minutes: Int?) = edit { it.copy(rule = it.rule.copy(dailyLimitMinutes = minutes)) }
    fun setMaxLaunches(count: Int?) = edit { it.copy(rule = it.rule.copy(maxLaunches = count)) }
    fun setPause(seconds: Int) = edit { it.copy(rule = it.rule.copy(pauseSeconds = seconds)) }
    fun setEscalation(on: Boolean) = edit { it.copy(rule = it.rule.copy(escalationEnabled = on)) }
    fun setEscalationByOpens(on: Boolean) = edit { it.copy(rule = RestrictionRule.withByOpens(it.rule, on)) }
    fun setEscalationMajor(minutes: Int) = edit { it.copy(rule = RestrictionRule.withMajor(it.rule, minutes)) }
    fun setEscalationMax(minutes: Int) = edit { it.copy(rule = RestrictionRule.withMax(it.rule, minutes)) }
    fun setWindow(start: Int?, end: Int?) = edit { it.copy(rule = it.rule.copy(allowedStartMinutes = start, allowedEndMinutes = end)) }
    fun setAppLimits(pkg: String, limits: AppLimits) = edit { p ->
        p.copy(apps = p.apps.map { if (it.packageName == pkg) it.copy(limits = limits) else it })
    }
    fun removeApp(pkg: String) = edit { p -> p.copy(apps = p.apps.filterNot { it.packageName == pkg }) }
    fun setApps(packages: List<String>) = edit { p ->
        val existing = p.apps.associateBy { it.packageName }
        p.copy(apps = packages.map { existing[it] ?: com.aiyu.rewire.domain.habit.ProtectedApp(it, p.id, p.level, true) })
    }
    fun delete() { habit.value?.let { habitRepo.delete(it.id) } }
}
