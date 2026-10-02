package com.rewire.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.withTransaction
import com.rewire.app.core.settings.ThemeMode
import com.rewire.app.core.settings.UserGoal
import com.rewire.app.domain.analytics.HabitEvent
import com.rewire.app.domain.habit.HabitProfile
import com.rewire.app.domain.warning.Warning
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.io.File

private val Context.legacyDataStore by preferencesDataStore("settings")

/**
 * One-time move of pre-Room data (JSON files + DataStore prefs) into the database.
 * Runs while the settings row is missing, i.e. first launch on this schema; old files are deleted
 * only after the import transaction commits, so a crash mid-way just retries next launch.
 */
// ponytail: drop this file (and the DataStore dependency) once no install older than 1.1 is in the wild.
object LegacyImport {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun runIfNeeded(context: Context, db: RewireDatabase) {
        if (db.settings().get() != null) return
        val files = context.filesDir
        val habitsFile = File(files, "habits.json")
        val warningsFile = File(files, "warnings.json")
        val eventsFile = File(files, "events.json")
        val prefsFile = context.preferencesDataStoreFile("settings")

        val habits = read<List<HabitProfile>>(habitsFile).orEmpty()
        val warnings = read<List<Warning>>(warningsFile).orEmpty()
        val events = read<List<HabitEvent>>(eventsFile).orEmpty()
        val settings = if (prefsFile.exists()) runCatching { context.legacyDataStore.data.first().toEntity() }.getOrNull() else null

        db.withTransaction {
            habits.forEachIndexed { i, p ->
                // created_at only orders the list; index keeps the old JSON order.
                db.habits().insert(p.toHabitEntity(createdAt = i.toLong()), p.toAppEntities(), p.toRuleEntity())
            }
            db.warnings().insertMissing(warnings.map { it.toEntity() })
            db.events().insert(events.map { it.toEntity() })
            db.settings().upsert(settings ?: SettingsEntity())
        }
        habitsFile.delete(); warningsFile.delete(); eventsFile.delete()
        // DataStore file stays readable until the process ends; deleting it is safe since nothing writes it now.
        prefsFile.delete()
    }

    private inline fun <reified T> read(file: File): T? =
        if (!file.exists()) null else runCatching { json.decodeFromString<T>(file.readText()) }.getOrNull() // corrupt -> skip, never crash

    private fun Preferences.toEntity(): SettingsEntity {
        fun b(k: String) = this[booleanPreferencesKey(k)]
        fun s(k: String) = this[stringPreferencesKey(k)]
        val d = SettingsEntity()
        return SettingsEntity(
            onboardingDone = b("onboarding_done") ?: d.onboardingDone,
            themeMode = s("theme_mode")?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode,
            dynamicColor = b("dynamic_color") ?: d.dynamicColor,
            notifyFocus = b("notif_focus") ?: d.notifyFocus,
            notifyGuard = b("notif_guard") ?: d.notifyGuard,
            notifySummary = b("notif_summary") ?: d.notifySummary,
            bypassMinor = b("bypass_minor") ?: d.bypassMinor,
            bypassMajor = b("bypass_major") ?: d.bypassMajor,
            bypassMax = b("bypass_max") ?: d.bypassMax,
            focusDndEnabled = b("focus_dnd_enabled") ?: d.focusDndEnabled,
            notificationPermissionAsked = b("notif_permission_asked") ?: d.notificationPermissionAsked,
            userName = s("user_name") ?: d.userName,
            userGoal = s("user_goal")?.let { runCatching { UserGoal.valueOf(it) }.getOrNull() },
            userReason = s("user_reason") ?: d.userReason,
            avatarShape = this[intPreferencesKey("avatar_shape")] ?: d.avatarShape,
        )
    }
}
