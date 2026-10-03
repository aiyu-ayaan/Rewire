package com.aiyu.rewire.data

import com.aiyu.rewire.domain.analytics.HabitEvent
import com.aiyu.rewire.domain.analytics.HabitEventType
import com.aiyu.rewire.domain.habit.Habit
import com.aiyu.rewire.domain.habit.HabitProfile
import com.aiyu.rewire.domain.habit.ProtectedApp
import com.aiyu.rewire.domain.habit.RestrictionRule
import com.aiyu.rewire.domain.habit.WarningLevel
import com.aiyu.rewire.domain.warning.Warning
import com.aiyu.rewire.domain.warning.WarningCategory
import com.aiyu.rewire.data.local.EventDao
import com.aiyu.rewire.data.local.FocusSessionDao
import com.aiyu.rewire.data.local.HabitDao
import com.aiyu.rewire.data.local.WarningDao
import com.aiyu.rewire.data.local.toAppEntities
import com.aiyu.rewire.data.local.toDomain
import com.aiyu.rewire.data.local.toEntity
import com.aiyu.rewire.data.local.toHabitEntity
import com.aiyu.rewire.data.local.toRuleEntity
import com.aiyu.rewire.domain.focus.FocusSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Room is the source of truth; each repo keeps a write-through in-memory copy so the accessibility
 * service and rule engine read rules synchronously (no suspend on the main-thread hot path).
 * Writes go through one serial writer, so they hit the database in the order they were made.
 */
// ponytail: whole tables cached in memory; fine for hundreds of habits / months of events. Page from Room if that grows.
class DbWriter(scope: CoroutineScope) {
    private val serial = CoroutineScope(scope.coroutineContext + Dispatchers.IO.limitedParallelism(1))
    fun write(block: suspend () -> Unit) { serial.launch { runCatching { block() } } }
}

/** One blocking read at startup (tiny tables), same as reading a prefs file. */
internal fun <T> loadNow(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { block() }

interface HabitRepository {
    val habits: StateFlow<List<HabitProfile>>
    fun habit(id: String): Flow<HabitProfile?>
    fun create(name: String, level: WarningLevel, packages: List<String>): HabitProfile
    fun update(profile: HabitProfile)
    fun delete(id: String)
    /** Re-reads the cache from Room after a bulk replace (backup import). */
    suspend fun reload()
}

class RoomHabitRepository(
    private val dao: HabitDao,
    private val writer: DbWriter,
    private val clock: () -> Long = System::currentTimeMillis,
) : HabitRepository {
    private val state = MutableStateFlow(loadNow { dao.all() }.mapNotNull { it.toDomain() })
    override val habits: StateFlow<List<HabitProfile>> = state.asStateFlow()

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
        state.update { it + profile }
        writer.write { dao.insert(profile.toHabitEntity(clock()), profile.toAppEntities(), profile.toRuleEntity()) }
        return profile
    }

    override fun update(profile: HabitProfile) {
        // Keep per-app level in sync with the habit rule; per-app overrides arrive with Phase 3.
        val synced = profile.copy(apps = profile.apps.map { it.copy(warningLevel = profile.rule.warningLevel) })
        state.update { list -> list.map { if (it.id == profile.id) synced else it } }
        writer.write { dao.update(synced.toHabitEntity(createdAt = 0), synced.toAppEntities(), synced.toRuleEntity()) }
    }

    override fun delete(id: String) {
        state.update { list -> list.filterNot { it.id == id } }
        writer.write { dao.delete(id) } // apps + rule cascade
    }

    override suspend fun reload() { state.value = dao.all().mapNotNull { it.toDomain() } }

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
    suspend fun reload()
}

/** Seeds from bundled JSON (res/raw/default_warnings.json), then keeps the user's edits. */
class RoomWarningRepository(
    private val dao: WarningDao,
    private val writer: DbWriter,
    defaults: List<Warning> = emptyList(),
) : WarningRepository {
    // Built-ins added in app updates reach existing libraries (insert-ignore); user edits to old ones are kept.
    private val state = MutableStateFlow(loadNow { dao.insertMissing(defaults.map { it.toEntity() }); dao.all() }.map { it.toDomain() })
    override val warnings: StateFlow<List<Warning>> = state.asStateFlow()

    override fun update(warning: Warning) {
        state.update { list -> list.map { if (it.id == warning.id) warning else it } }
        writer.write { dao.update(warning.toEntity()) }
    }

    override fun addCustom(level: WarningLevel, title: String, message: String, motivation: String): Warning {
        val w = Warning(
            id = "custom-${UUID.randomUUID()}", category = WarningCategory.CUSTOM, level = level,
            title = title.trim(), message = message.trim(), motivationalMessage = motivation.trim(), custom = true,
        )
        state.update { it + w }
        writer.write { dao.insertMissing(listOf(w.toEntity())) }
        return w
    }

    override fun delete(id: String) {
        state.update { list -> list.filterNot { it.id == id && it.custom } }
        writer.write { dao.deleteCustom(id) }
    }

    override suspend fun reload() { state.value = dao.all().map { it.toDomain() } }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun defaults(defaultsJson: String) = json.decodeFromString<List<Warning>>(defaultsJson)
    }
}

interface EventRepository {
    val events: StateFlow<List<HabitEvent>>
    fun log(type: HabitEventType, packageName: String? = null, habitId: String? = null, metadata: Map<String, String> = emptyMap())
    /** Re-reads the cache from Room after history was cleared or replaced. */
    suspend fun reload()
}

/** Full history stays in Room; memory holds the recent window Matrix and the engine read. */
class RoomEventRepository(
    private val dao: EventDao,
    private val writer: DbWriter,
    private val clock: () -> Long = System::currentTimeMillis,
) : EventRepository {
    private val state = MutableStateFlow(loadNow { dao.since(clock() - CACHE_WINDOW_MILLIS) }.map { it.toDomain() })
    override val events: StateFlow<List<HabitEvent>> = state.asStateFlow()

    override fun log(type: HabitEventType, packageName: String?, habitId: String?, metadata: Map<String, String>) {
        val e = HabitEvent(UUID.randomUUID().toString(), type, packageName, habitId, clock(), metadata)
        state.update { it + e }
        writer.write { dao.insert(listOf(e.toEntity())) }
    }

    override suspend fun reload() { state.value = dao.since(clock() - CACHE_WINDOW_MILLIS).map { it.toDomain() } }

    private companion object {
        const val CACHE_WINDOW_MILLIS = 90L * 24 * 60 * 60 * 1000
    }
}

interface FocusSessionRepository {
    /** Finished sessions, newest first. */
    val history: Flow<List<FocusSession>>
    /** Session that was running when the process last died, if any. */
    fun active(): FocusSession?
    fun save(session: FocusSession)
    fun setNote(id: String, note: String?)
    fun delete(id: String)
}

class RoomFocusSessionRepository(private val dao: FocusSessionDao, private val writer: DbWriter) : FocusSessionRepository {
    override val history = dao.history().map { list -> list.map { it.toDomain() } }
    override fun active() = loadNow { dao.active() }?.toDomain()
    override fun save(session: FocusSession) = writer.write { dao.upsert(session.toEntity()) }
    override fun setNote(id: String, note: String?) = writer.write { dao.setNote(id, note?.trim()?.ifBlank { null }) }
    override fun delete(id: String) = writer.write { dao.delete(id) }
}
