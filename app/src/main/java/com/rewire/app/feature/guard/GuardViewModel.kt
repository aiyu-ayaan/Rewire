package com.rewire.app.feature.guard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rewire.app.AppContainer
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

class GuardViewModel(private val c: AppContainer) : ViewModel() {
    val habits: StateFlow<List<HabitProfile>> = c.habits.habits

    val today: StateFlow<DailyMetrics> = c.events.events
        .map { MetricsCalculator.daily(it, LocalDate.now(), ZoneId.systemDefault()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MetricsCalculator.daily(emptyList(), LocalDate.now(), ZoneId.systemDefault()))

    fun create(name: String, level: WarningLevel, packages: List<String>) = c.habits.create(name, level, packages)

    fun setEnabled(profile: HabitProfile, enabled: Boolean) =
        c.habits.update(profile.copy(habit = profile.habit.copy(enabled = enabled)))
}

class HabitDetailViewModel(private val c: AppContainer, id: String) : ViewModel() {
    val habit: StateFlow<HabitProfile?> = c.habits.habit(id).stateIn(viewModelScope, SharingStarted.Eagerly, c.habits.habits.value.find { it.id == id })

    private fun edit(block: (HabitProfile) -> HabitProfile) { habit.value?.let { c.habits.update(block(it)) } }

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
    fun delete() { habit.value?.let { c.habits.delete(it.id) } }
}
