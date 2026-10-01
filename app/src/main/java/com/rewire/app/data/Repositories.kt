package com.rewire.app.data

import com.rewire.app.domain.analytics.HabitEvent
import com.rewire.app.domain.analytics.HabitEventType
import com.rewire.app.domain.habit.Habit
import com.rewire.app.domain.habit.HabitProfile
import com.rewire.app.domain.habit.ProtectedApp
import com.rewire.app.domain.habit.RestrictionRule
import com.rewire.app.domain.habit.WarningLevel
import com.rewire.app.domain.warning.Warning
import com.rewire.app.domain.warning.WarningCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import java.util.UUID

// ponytail: all repos in memory for Phase 1; Room implementations replace these in Phase 2 (same interfaces).

interface HabitRepository {
    val habits: StateFlow<List<HabitProfile>>
    fun habit(id: String): Flow<HabitProfile?>
    fun create(name: String, level: WarningLevel, packages: List<String>): HabitProfile
    fun update(profile: HabitProfile)
    fun delete(id: String)
}

class InMemoryHabitRepository : HabitRepository {
    private val state = MutableStateFlow<List<HabitProfile>>(emptyList())
    override val habits = state.asStateFlow()

    override fun habit(id: String) = state.map { list -> list.find { it.id == id } }

    override fun create(name: String, level: WarningLevel, packages: List<String>): HabitProfile {
        val id = UUID.randomUUID().toString()
        val profile = HabitProfile(
            habit = Habit(id, name.trim(), null, enabled = true),
            apps = packages.map { ProtectedApp(it, id, level, enabled = true) },
            rule = RestrictionRule(
                id = UUID.randomUUID().toString(), habitId = id,
                dailyLimitMinutes = null, allowedStartMinutes = null, allowedEndMinutes = null,
                maxLaunches = null, warningLevel = level, pauseSeconds = DEFAULT_PAUSE_SECONDS,
            ),
        )
        state.update { it + profile }
        return profile
    }

    override fun update(profile: HabitProfile) {
        // Keep per-app level in sync with the habit rule; per-app overrides arrive with Phase 3.
        val synced = profile.copy(apps = profile.apps.map { it.copy(warningLevel = profile.rule.warningLevel) })
        state.update { list -> list.map { if (it.id == profile.id) synced else it } }
    }

    override fun delete(id: String) = state.update { list -> list.filterNot { it.id == id } }

    companion object {
        /** Initial Major pause for new habits; user edits per habit. */
        const val DEFAULT_PAUSE_SECONDS = 5
    }
}

interface WarningRepository {
    val warnings: StateFlow<List<Warning>>
    fun update(warning: Warning)
    fun addCustom(level: WarningLevel, title: String, message: String, motivation: String): Warning
    fun delete(id: String)
}

/** Seeds from bundled JSON (res/raw/default_warnings.json) so text never lives in UI code. */
class InMemoryWarningRepository(defaultsJson: String) : WarningRepository {
    private val state = MutableStateFlow(json.decodeFromString<List<Warning>>(defaultsJson))
    override val warnings = state.asStateFlow()

    override fun update(warning: Warning) = state.update { list -> list.map { if (it.id == warning.id) warning else it } }

    override fun addCustom(level: WarningLevel, title: String, message: String, motivation: String): Warning {
        val w = Warning(
            id = "custom-${UUID.randomUUID()}", category = WarningCategory.CUSTOM, level = level,
            title = title.trim(), message = message.trim(), motivationalMessage = motivation.trim(), custom = true,
        )
        state.update { it + w }
        return w
    }

    override fun delete(id: String) = state.update { list -> list.filterNot { it.id == id && it.custom } }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

interface EventRepository {
    val events: StateFlow<List<HabitEvent>>
    fun log(type: HabitEventType, packageName: String? = null, habitId: String? = null, metadata: Map<String, String> = emptyMap())
}

class InMemoryEventRepository(private val clock: () -> Long = System::currentTimeMillis) : EventRepository {
    private val state = MutableStateFlow<List<HabitEvent>>(emptyList())
    override val events = state.asStateFlow()

    override fun log(type: HabitEventType, packageName: String?, habitId: String?, metadata: Map<String, String>) =
        state.update { it + HabitEvent(UUID.randomUUID().toString(), type, packageName, habitId, clock(), metadata) }
}
