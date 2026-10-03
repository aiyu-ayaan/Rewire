package com.aiyu.rewire.data

import androidx.room.withTransaction
import com.aiyu.rewire.data.local.RewireDatabase
import com.aiyu.rewire.data.local.toAppEntities
import com.aiyu.rewire.data.local.toDomain
import com.aiyu.rewire.data.local.toEntity
import com.aiyu.rewire.data.local.toHabitEntity
import com.aiyu.rewire.data.local.toRuleEntity
import com.aiyu.rewire.domain.backup.BackupException
import com.aiyu.rewire.domain.backup.BackupSnapshot
import com.aiyu.rewire.data.local.SettingsEntity
import com.aiyu.rewire.domain.focus.FocusPreset
import kotlinx.coroutines.flow.first

/** Export / import / clear for the whole database. Room is touched in one transaction, caches reload after. */
class BackupRepository(
    private val db: RewireDatabase,
    private val habits: HabitRepository,
    private val warnings: WarningRepository,
    private val events: EventRepository,
    private val presets: FocusPresetRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun export(): BackupSnapshot {
        val presetsSnapshot = presets.userPresets.first()
        return exportRoom(presetsSnapshot)
    }

    private suspend fun exportRoom(presetsSnapshot: List<FocusPreset>): BackupSnapshot = db.withTransaction {
        BackupSnapshot(
            exportedAt = clock(),
            habits = db.habits().all().mapNotNull { it.toDomain() },
            warnings = db.warnings().all().map { it.toDomain() },
            events = db.events().all().map { it.toDomain() },
            focusSessions = db.focusSessions().finished().map { it.toDomain() },
            settings = (db.settings().get() ?: SettingsEntity()).toDomain(),
            focusPresets = presetsSnapshot,
        )
    }

    /** Replaces everything with [snapshot]. All-or-nothing: any failure rolls the transaction back. */
    suspend fun import(snapshot: BackupSnapshot) {
        db.withTransaction {
            if (db.focusSessions().active() != null) throw BackupException(BackupException.Reason.FOCUS_ACTIVE)
            db.habits().deleteAll() // apps + rules cascade
            db.warnings().deleteAll()
            db.events().deleteAll()
            db.focusSessions().deleteFinished()
            snapshot.habits.forEachIndexed { i, p -> db.habits().insert(p.toHabitEntity(createdAt = i.toLong()), p.toAppEntities(), p.toRuleEntity()) }
            db.warnings().insertMissing(snapshot.warnings.map { it.toEntity() })
            db.events().insert(snapshot.events.map { it.toEntity() })
            snapshot.focusSessions.forEach { db.focusSessions().upsert(it.toEntity()) }
            db.settings().upsert(snapshot.settings.toEntity())
        }
        snapshot.focusPresets?.let { presets.replaceAll(it) }
        reloadCaches()
    }

    /** Events + finished focus sessions only; habits, rules, warnings and settings stay. */
    suspend fun clearHistory() {
        db.withTransaction {
            db.events().deleteAll()
            db.focusSessions().deleteFinished()
        }
        events.reload()
    }

    private suspend fun reloadCaches() {
        habits.reload(); warnings.reload(); events.reload()
    }
}
