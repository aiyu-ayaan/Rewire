package com.aiyu.rewire.data.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

@Database(
    entities = [
        HabitEntity::class, ProtectedAppEntity::class, RestrictionRuleEntity::class,
        WarningEntity::class, HabitEventEntity::class, FocusSessionEntity::class, SettingsEntity::class,
    ],
    version = 2,
    exportSchema = true,
    // 1 -> 2: four settings columns for app updates (defaults declared on the columns).
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(Converters::class)
abstract class RewireDatabase : RoomDatabase() {
    abstract fun habits(): HabitDao
    abstract fun warnings(): WarningDao
    abstract fun events(): EventDao
    abstract fun focusSessions(): FocusSessionDao
    abstract fun settings(): SettingsDao

    companion object {
        const val NAME = "rewire.db"

        // Schema changes ship a Migration + exported schema; never fallbackToDestructiveMigration (user history).
        fun create(context: Context): RewireDatabase =
            Room.databaseBuilder(context, RewireDatabase::class.java, NAME).build()
    }
}

class Converters {
    @TypeConverter fun fromMetadata(map: Map<String, String>): String = if (map.isEmpty()) "{}" else json.encodeToString(map)
    @TypeConverter fun toMetadata(raw: String): Map<String, String> = runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())

    private companion object {
        val json = Json
    }
}

@Dao
interface HabitDao {
    @Transaction
    @Query("SELECT * FROM habits ORDER BY created_at")
    suspend fun all(): List<HabitWithDetails>

    @Query("DELETE FROM habits")
    suspend fun deleteAll()

    @Insert suspend fun insertHabit(habit: HabitEntity)
    @Upsert suspend fun upsertRule(rule: RestrictionRuleEntity)
    @Insert suspend fun insertApps(apps: List<ProtectedAppEntity>)

    /** Edits leave created_at (list position) untouched. */
    @Query("UPDATE habits SET name = :name, description = :description, enabled = :enabled WHERE id = :id")
    suspend fun updateHabit(id: String, name: String, description: String?, enabled: Boolean)

    @Query("DELETE FROM protected_apps WHERE habit_id = :habitId")
    suspend fun deleteApps(habitId: String)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun delete(id: String)

    // Habit, its app set and its rule change together or not at all.

    @Transaction
    suspend fun insert(habit: HabitEntity, apps: List<ProtectedAppEntity>, rule: RestrictionRuleEntity) {
        insertHabit(habit)
        insertApps(apps)
        upsertRule(rule)
    }

    @Transaction
    suspend fun update(habit: HabitEntity, apps: List<ProtectedAppEntity>, rule: RestrictionRuleEntity) {
        updateHabit(habit.id, habit.name, habit.description, habit.enabled)
        deleteApps(habit.id)
        insertApps(apps)
        upsertRule(rule)
    }
}

@Dao
interface WarningDao {
    // rowid = insertion order: built-ins first (bundled order), then customs as created.
    @Query("SELECT * FROM warnings ORDER BY rowid")
    suspend fun all(): List<WarningEntity>

    /** Seeds / new built-ins only; never overwrites the user's edits. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(warnings: List<WarningEntity>)

    @Query("DELETE FROM warnings")
    suspend fun deleteAll()

    @Update suspend fun update(warning: WarningEntity)

    @Query("DELETE FROM warnings WHERE id = :id AND custom = 1")
    suspend fun deleteCustom(id: String)
}

@Dao
interface EventDao {
    @Query("SELECT * FROM habit_events WHERE timestamp >= :since ORDER BY timestamp")
    suspend fun since(since: Long): List<HabitEventEntity>

    @Query("SELECT * FROM habit_events ORDER BY timestamp")
    suspend fun all(): List<HabitEventEntity>

    @Query("DELETE FROM habit_events")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(events: List<HabitEventEntity>)
}

@Dao
interface FocusSessionDao {
    @Query("SELECT * FROM focus_sessions WHERE status IN ('COMPLETED', 'CANCELLED') ORDER BY started_at DESC")
    fun history(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE status IN ('FOCUSING', 'BREAK', 'PAUSED') ORDER BY started_at DESC LIMIT 1")
    suspend fun active(): FocusSessionEntity?

    @Query("SELECT * FROM focus_sessions WHERE status IN ('COMPLETED', 'CANCELLED') ORDER BY started_at")
    suspend fun finished(): List<FocusSessionEntity>

    /** History only: a running session is live state, never cleared. */
    @Query("DELETE FROM focus_sessions WHERE status IN ('COMPLETED', 'CANCELLED')")
    suspend fun deleteFinished()

    @Upsert suspend fun upsert(session: FocusSessionEntity)

    @Query("UPDATE focus_sessions SET note = :note WHERE id = :id")
    suspend fun setNote(id: String, note: String?)

    @Query("DELETE FROM focus_sessions WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 0")
    fun observe(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 0")
    suspend fun get(): SettingsEntity?

    @Upsert suspend fun upsert(settings: SettingsEntity)

    @Transaction
    suspend fun edit(transform: (SettingsEntity) -> SettingsEntity) = upsert(transform(get() ?: SettingsEntity()))
}
