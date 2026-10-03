package com.aiyu.rewire.domain.backup

import com.aiyu.rewire.core.settings.Settings
import com.aiyu.rewire.domain.analytics.HabitEvent
import com.aiyu.rewire.domain.focus.FocusPreset
import com.aiyu.rewire.domain.focus.FocusSession
import com.aiyu.rewire.domain.habit.HabitProfile
import com.aiyu.rewire.domain.warning.Warning
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Everything the user owns, as one versioned file. Finished focus sessions only (an active one is live state). */
@Serializable
data class BackupSnapshot(
    val format: String = BackupCodec.FORMAT,
    val version: Int = BackupCodec.VERSION,
    val exportedAt: Long,
    val habits: List<HabitProfile>,
    val warnings: List<Warning>,
    val events: List<HabitEvent>,
    val focusSessions: List<FocusSession>,
    val settings: Settings,
    /** Optional: absent in older backups, in which case restore leaves the user's presets untouched. */
    val focusPresets: List<FocusPreset>? = null,
)

class BackupException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason { MALFORMED, NOT_A_BACKUP, UNSUPPORTED_VERSION, INCONSISTENT, FOCUS_ACTIVE }
}

object BackupCodec {
    const val FORMAT = "rewire-backup"
    const val VERSION = 1

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(snapshot: BackupSnapshot): String = json.encodeToString(BackupSnapshot.serializer(), snapshot)

    /** Parses and validates; throws [BackupException] on anything unsafe to write to the database. */
    fun decode(text: String): BackupSnapshot {
        val snapshot = try {
            json.decodeFromString(BackupSnapshot.serializer(), text)
        } catch (e: SerializationException) {
            throw BackupException(BackupException.Reason.MALFORMED, e)
        } catch (e: IllegalArgumentException) {
            throw BackupException(BackupException.Reason.MALFORMED, e)
        }
        if (snapshot.format != FORMAT) throw BackupException(BackupException.Reason.NOT_A_BACKUP)
        if (snapshot.version !in 1..VERSION) throw BackupException(BackupException.Reason.UNSUPPORTED_VERSION)
        if (!snapshot.isConsistent()) throw BackupException(BackupException.Reason.INCONSISTENT)
        return snapshot
    }

    private fun BackupSnapshot.isConsistent(): Boolean =
        habits.map { it.id }.distinct().size == habits.size &&
            habits.all { p -> p.rule.habitId == p.id && p.apps.all { it.habitId == p.id } && p.apps.map { it.packageName }.distinct().size == p.apps.size } &&
            warnings.map { it.id }.distinct().size == warnings.size &&
            events.map { it.id }.distinct().size == events.size &&
            focusSessions.map { it.id }.distinct().size == focusSessions.size &&
            focusSessions.all { it.isFinished && it.state.startedAt != null }
}
