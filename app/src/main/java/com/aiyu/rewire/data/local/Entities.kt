package com.aiyu.rewire.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.aiyu.rewire.core.settings.ThemeMode
import com.aiyu.rewire.core.settings.UserGoal
import com.aiyu.rewire.domain.analytics.HabitEventType
import com.aiyu.rewire.domain.focus.FocusSessionStatus
import com.aiyu.rewire.domain.habit.WarningLevel
import com.aiyu.rewire.domain.update.UpdateChannel
import com.aiyu.rewire.domain.warning.WarningCategory

// Enums are stored by name (Room default), so reordering an enum never corrupts rows.

// ---- Guard ---------------------------------------------------------------------------------------

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val enabled: Boolean,
    /** Keeps the user's list order stable. */
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** Apps belong to exactly one habit; removing the habit removes its apps. */
@Entity(
    tableName = "protected_apps",
    primaryKeys = ["habit_id", "package_name"],
    foreignKeys = [ForeignKey(entity = HabitEntity::class, parentColumns = ["id"], childColumns = ["habit_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("package_name")],
)
data class ProtectedAppEntity(
    @ColumnInfo(name = "habit_id") val habitId: String,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "warning_level") val warningLevel: WarningLevel,
    val enabled: Boolean,
)

/** One rule per habit (unique habit_id). Times are minutes from midnight; null = no boundary. */
@Entity(
    tableName = "restriction_rules",
    foreignKeys = [ForeignKey(entity = HabitEntity::class, parentColumns = ["id"], childColumns = ["habit_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("habit_id", unique = true)],
)
data class RestrictionRuleEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "habit_id") val habitId: String,
    @ColumnInfo(name = "daily_limit_minutes") val dailyLimitMinutes: Int?,
    @ColumnInfo(name = "allowed_start_minutes") val allowedStartMinutes: Int?,
    @ColumnInfo(name = "allowed_end_minutes") val allowedEndMinutes: Int?,
    @ColumnInfo(name = "max_launches") val maxLaunches: Int?,
    @ColumnInfo(name = "warning_level") val warningLevel: WarningLevel,
    @ColumnInfo(name = "pause_seconds") val pauseSeconds: Int,
    // v3: smart escalation. Defaults live in the columns so the 2 -> 3 auto-migration can add them.
    @ColumnInfo(name = "escalation_enabled", defaultValue = "0") val escalationEnabled: Boolean = false,
    @ColumnInfo(name = "escalation_major_minutes", defaultValue = "20") val escalationMajorMinutes: Int = 20,
    @ColumnInfo(name = "escalation_max_minutes", defaultValue = "40") val escalationMaxMinutes: Int = 40,
)

data class HabitWithDetails(
    @Embedded val habit: HabitEntity,
    @Relation(parentColumn = "id", entityColumn = "habit_id") val apps: List<ProtectedAppEntity>,
    @Relation(parentColumn = "id", entityColumn = "habit_id") val rule: RestrictionRuleEntity?,
)

// ---- Warning library -----------------------------------------------------------------------------

@Entity(tableName = "warnings", indices = [Index("level")])
data class WarningEntity(
    @PrimaryKey val id: String,
    val category: WarningCategory,
    val level: WarningLevel,
    val title: String,
    val message: String,
    @ColumnInfo(name = "motivational_message") val motivationalMessage: String,
    val enabled: Boolean,
    val favorite: Boolean,
    val custom: Boolean,
)

// ---- Event log -----------------------------------------------------------------------------------

/**
 * Append-only log that powers Matrix. No FK to habits on purpose: history outlives a deleted habit.
 * [metadata] is a JSON object of small string pairs (see [Converters]).
 */
@Entity(
    tableName = "habit_events",
    indices = [Index("timestamp"), Index(value = ["habit_id", "type", "timestamp"])],
)
data class HabitEventEntity(
    @PrimaryKey val id: String,
    val type: HabitEventType,
    @ColumnInfo(name = "package_name") val packageName: String?,
    @ColumnInfo(name = "habit_id") val habitId: String?,
    val timestamp: Long,
    val metadata: Map<String, String>,
)

// ---- Focus ---------------------------------------------------------------------------------------

/**
 * One row per session. While active it holds the full timer state (so a killed process resumes
 * exactly); once COMPLETED / CANCELLED it is history, with the optional achievement note.
 */
@Entity(tableName = "focus_sessions", indices = [Index("status"), Index("started_at")])
data class FocusSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "focus_minutes") val focusMinutes: Int,
    @ColumnInfo(name = "break_minutes") val breakMinutes: Int,
    val cycles: Int,
    /** Length of one "minute": 60 000 except the debug quick test. */
    @ColumnInfo(name = "unit_millis") val unitMillis: Long,
    val status: FocusSessionStatus,
    /** Current (or last reached) cycle, 1-based. */
    val cycle: Int,
    @ColumnInfo(name = "phase_ends_at") val phaseEndsAt: Long?,
    @ColumnInfo(name = "paused_remaining") val pausedRemaining: Long?,
    @ColumnInfo(name = "paused_from") val pausedFrom: FocusSessionStatus?,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long?,
    @ColumnInfo(name = "focused_millis") val focusedMillis: Long,
    val note: String?,
)

// ---- Settings ------------------------------------------------------------------------------------

/** Single row (id = 0). Typed columns instead of key/value so every setting has a real default. */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "onboarding_done") val onboardingDone: Boolean = false,
    @ColumnInfo(name = "theme_mode") val themeMode: ThemeMode = ThemeMode.SYSTEM,
    @ColumnInfo(name = "dynamic_color") val dynamicColor: Boolean = false,
    @ColumnInfo(name = "notify_focus") val notifyFocus: Boolean = true,
    @ColumnInfo(name = "notify_guard") val notifyGuard: Boolean = true,
    @ColumnInfo(name = "notify_summary") val notifySummary: Boolean = true,
    // CLAUDE.md §7 recommended defaults: Minor bypass on, Major configurable (off), Max off.
    @ColumnInfo(name = "bypass_minor") val bypassMinor: Boolean = true,
    @ColumnInfo(name = "bypass_major") val bypassMajor: Boolean = false,
    @ColumnInfo(name = "bypass_max") val bypassMax: Boolean = false,
    @ColumnInfo(name = "focus_dnd_enabled") val focusDndEnabled: Boolean = true,
    @ColumnInfo(name = "notification_permission_asked") val notificationPermissionAsked: Boolean = false,
    @ColumnInfo(name = "user_name") val userName: String = "",
    @ColumnInfo(name = "user_goal") val userGoal: UserGoal? = null,
    @ColumnInfo(name = "user_reason") val userReason: String = "",
    @ColumnInfo(name = "avatar_shape") val avatarShape: Int = 0,
    // v2: app self-update. Defaults live in the column too so the 1 -> 2 auto-migration can add them.
    @ColumnInfo(name = "updates_enabled", defaultValue = "1") val updatesEnabled: Boolean = true,
    /** null = follow the channel this build belongs to. */
    @ColumnInfo(name = "update_channel") val updateChannel: UpdateChannel? = null,
    @ColumnInfo(name = "update_snoozed_until", defaultValue = "0") val updateSnoozedUntil: Long = 0,
    @ColumnInfo(name = "update_last_checked", defaultValue = "0") val updateLastChecked: Long = 0,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
