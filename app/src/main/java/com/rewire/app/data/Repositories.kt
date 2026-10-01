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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.util.UUID

// Repos persist to small JSON files (JsonStore); Room replaces the stores in Phase 2 behind the same interfaces.

interface HabitRepository {
    val habits: StateFlow<List<HabitProfile>>
    fun habit(id: String): Flow<HabitProfile?>
    fun create(name: String, level: WarningLevel, packages: List<String>): HabitProfile
    fun update(profile: HabitProfile)
    fun delete(id: String)
}

class PersistentHabitRepository(private val store: JsonStore<List<HabitProfile>>) : HabitRepository {
    override val habits = store.value

    override fun habit(id: String) = habits.map { list -> list.find { it.id == id } }

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
        store.update { it + profile }
        return profile
    }

    override fun update(profile: HabitProfile) {
        // Keep per-app level in sync with the habit rule; per-app overrides arrive with Phase 3.
        val synced = profile.copy(apps = profile.apps.map { it.copy(warningLevel = profile.rule.warningLevel) })
        store.update { list -> list.map { if (it.id == profile.id) synced else it } }
    }

    override fun delete(id: String) = store.update { list -> list.filterNot { it.id == id } }

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

/** Seeds from bundled JSON (res/raw/default_warnings.json) on first run, then keeps the user's edits. */
class PersistentWarningRepository(
    private val store: JsonStore<List<Warning>>,
    defaults: List<Warning> = emptyList(),
) : WarningRepository {
    override val warnings = store.value

    init {
        // Built-ins added in app updates reach existing libraries; user edits to old ones are kept.
        val have = warnings.value.mapTo(HashSet()) { it.id }
        val missing = defaults.filterNot { it.id in have }
        if (missing.isNotEmpty()) store.update { it + missing }
    }

    override fun update(warning: Warning) = store.update { list -> list.map { if (it.id == warning.id) warning else it } }

    override fun addCustom(level: WarningLevel, title: String, message: String, motivation: String): Warning {
        val w = Warning(
            id = "custom-${UUID.randomUUID()}", category = WarningCategory.CUSTOM, level = level,
            title = title.trim(), message = message.trim(), motivationalMessage = motivation.trim(), custom = true,
        )
        store.update { it + w }
        return w
    }

    override fun delete(id: String) = store.update { list -> list.filterNot { it.id == id && it.custom } }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun defaults(defaultsJson: String) = json.decodeFromString<List<Warning>>(defaultsJson)
    }
}

interface EventRepository {
    val events: StateFlow<List<HabitEvent>>
    fun log(type: HabitEventType, packageName: String? = null, habitId: String? = null, metadata: Map<String, String> = emptyMap())
}

class PersistentEventRepository(
    private val store: JsonStore<List<HabitEvent>>,
    private val clock: () -> Long = System::currentTimeMillis,
) : EventRepository {
    override val events = store.value

    init {
        // Keep the file small: Matrix needs at most the last month or so in Phase 1.
        val cutoff = clock() - RETENTION_MILLIS
        if (events.value.any { it.timestamp < cutoff }) store.update { list -> list.filter { it.timestamp >= cutoff } }
    }

    override fun log(type: HabitEventType, packageName: String?, habitId: String?, metadata: Map<String, String>) =
        store.update { it + HabitEvent(UUID.randomUUID().toString(), type, packageName, habitId, clock(), metadata) }

    private companion object {
        const val RETENTION_MILLIS = 90L * 24 * 60 * 60 * 1000
    }
}
