package com.aiyu.rewire.domain

import com.aiyu.rewire.core.settings.FocusBypass
import com.aiyu.rewire.core.settings.NotificationCategory
import com.aiyu.rewire.core.settings.Settings
import com.aiyu.rewire.core.settings.ThemeMode
import com.aiyu.rewire.core.settings.UserGoal
import com.aiyu.rewire.core.settings.UserProfile
import com.aiyu.rewire.domain.analytics.HabitEvent
import com.aiyu.rewire.domain.analytics.HabitEventType
import com.aiyu.rewire.domain.backup.BackupCodec
import com.aiyu.rewire.domain.backup.BackupException
import com.aiyu.rewire.domain.backup.BackupSnapshot
import com.aiyu.rewire.domain.goals.Goals
import com.aiyu.rewire.domain.focus.FocusConfig
import com.aiyu.rewire.domain.focus.FocusPreset
import com.aiyu.rewire.domain.focus.FocusSession
import com.aiyu.rewire.domain.focus.FocusSessionStatus
import com.aiyu.rewire.domain.focus.FocusState
import com.aiyu.rewire.domain.habit.AppLimits
import com.aiyu.rewire.domain.habit.Habit
import com.aiyu.rewire.domain.habit.HabitProfile
import com.aiyu.rewire.domain.habit.ProtectedApp
import com.aiyu.rewire.domain.habit.RestrictionRule
import com.aiyu.rewire.domain.habit.WarningLevel
import com.aiyu.rewire.domain.warning.Warning
import com.aiyu.rewire.domain.warning.WarningCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class BackupCodecTest {

    private val habit = HabitProfile(
        Habit("h1", "Scrolling", "less", true),
        listOf(ProtectedApp("com.a", "h1", WarningLevel.MAJOR, true)),
        RestrictionRule("r1", "h1", 45, 540, 570, 3, WarningLevel.MAJOR, 5),
    )
    private val finished = FocusSession(
        "f1",
        FocusState(FocusSessionStatus.COMPLETED, FocusConfig(25, 5, 2), cycle = 2, startedAt = 1000, completedAt = 9000),
        focusedMillis = 8000, note = "shipped",
    )
    private val snapshot = BackupSnapshot(
        exportedAt = 123,
        habits = listOf(habit),
        warnings = listOf(Warning("w1", WarningCategory.CUSTOM, WarningLevel.MINOR, "t", "m", "mm", favorite = true, custom = true)),
        events = listOf(HabitEvent("e1", HabitEventType.APP_BLOCKED, "com.a", "h1", 50, mapOf("reason" to "DAILY_LIMIT"))),
        focusSessions = listOf(finished),
        settings = Settings(
            true, ThemeMode.DARK, false, mapOf(NotificationCategory.FOCUS to false), FocusBypass(true, false, false),
            true, true, UserProfile("Ayaan", UserGoal.DEEP_WORK, "control", 2),
        ),
    )

    private fun reason(text: String): BackupException.Reason =
        try { BackupCodec.decode(text); fail("expected rejection"); error("unreachable") } catch (e: BackupException) { e.reason }

    @Test fun `round trip keeps everything`() {
        assertEquals(snapshot, BackupCodec.decode(BackupCodec.encode(snapshot)))
    }

    @Test fun `focus presets round trip and are optional`() {
        val withPresets = snapshot.copy(focusPresets = listOf(FocusPreset("p1", "Mine", 30, 5, 2)))
        assertEquals(withPresets, BackupCodec.decode(BackupCodec.encode(withPresets)))
        // A backup written before presets existed has no key: decodes to null (restore leaves presets alone).
        val legacy = BackupCodec.encode(snapshot).replace(",\"focusPresets\":null", "")
        assertEquals(null, BackupCodec.decode(legacy).focusPresets)
    }

    @Test fun `per-app limits round trip and older files without them still load`() {
        val own = habit.copy(apps = listOf(ProtectedApp("com.a", "h1", WarningLevel.MAJOR, true, AppLimits(ownDailyLimit = true, dailyLimitMinutes = 10, ownWindow = true, allowedStartMinutes = 1200, allowedEndMinutes = 1260))))
        val withLimits = snapshot.copy(habits = listOf(own))
        assertEquals(withLimits, BackupCodec.decode(BackupCodec.encode(withLimits)))
        // Written before per-app limits existed: no key, the app follows its habit.
        val legacy = BackupCodec.encode(snapshot).replace(Regex(",\"limits\":\\{[^}]*\\}"), "")
        assertEquals(false, "\"limits\"" in legacy)
        assertEquals(snapshot, BackupCodec.decode(legacy))
    }

    @Test fun `unknown keys from a newer minor writer are ignored`() {
        val text = BackupCodec.encode(snapshot).replaceFirst("{", "{\"future\":1,")
        assertEquals(snapshot, BackupCodec.decode(text))
    }

    @Test fun `goals round trip and older files without goals still load`() {
        val withGoals = snapshot.copy(goals = Goals(45, 2))
        assertEquals(Goals(45, 2), BackupCodec.decode(BackupCodec.encode(withGoals)).goals)
        val legacy = BackupCodec.encode(snapshot).replace(Regex(",\"goals\":null"), "")
        assertEquals(null, BackupCodec.decode(legacy).goals)
        assertEquals(BackupException.Reason.INCONSISTENT, reason(BackupCodec.encode(snapshot.copy(goals = Goals(dailyFocusMinutes = 1)))))
    }

    @Test fun `screen time round trips and older files without it still load`() {
        val with = snapshot.copy(screenTime = mapOf("2026-10-03" to 95))
        assertEquals(mapOf("2026-10-03" to 95), BackupCodec.decode(BackupCodec.encode(with)).screenTime)
        val legacy = BackupCodec.encode(snapshot).replace(Regex(",\"screenTime\":null"), "")
        assertEquals(null, BackupCodec.decode(legacy).screenTime)
    }

    @Test fun `garbage is malformed`() {
        assertEquals(BackupException.Reason.MALFORMED, reason("not json"))
        assertEquals(BackupException.Reason.MALFORMED, reason("{}"))
        assertEquals(BackupException.Reason.MALFORMED, reason(""))
    }

    @Test fun `wrong format marker is rejected`() {
        assertEquals(BackupException.Reason.NOT_A_BACKUP, reason(BackupCodec.encode(snapshot.copy(format = "other"))))
    }

    @Test fun `newer or zero version is rejected`() {
        assertEquals(BackupException.Reason.UNSUPPORTED_VERSION, reason(BackupCodec.encode(snapshot.copy(version = BackupCodec.VERSION + 1))))
        assertEquals(BackupException.Reason.UNSUPPORTED_VERSION, reason(BackupCodec.encode(snapshot.copy(version = 0))))
    }

    @Test fun `duplicate ids and dangling references are rejected`() {
        assertEquals(BackupException.Reason.INCONSISTENT, reason(BackupCodec.encode(snapshot.copy(events = snapshot.events + snapshot.events))))
        val orphanRule = habit.copy(rule = habit.rule.copy(habitId = "other"))
        assertEquals(BackupException.Reason.INCONSISTENT, reason(BackupCodec.encode(snapshot.copy(habits = listOf(orphanRule)))))
    }

    @Test fun `unfinished focus session is rejected`() {
        val live = finished.copy(state = finished.state.copy(status = FocusSessionStatus.FOCUSING))
        assertEquals(BackupException.Reason.INCONSISTENT, reason(BackupCodec.encode(snapshot.copy(focusSessions = listOf(live)))))
    }
}
