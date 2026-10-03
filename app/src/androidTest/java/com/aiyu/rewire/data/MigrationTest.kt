package com.aiyu.rewire.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.aiyu.rewire.data.local.RewireDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RewireDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To2_keepsRowsAndDefaultsNewSettingsColumns() {
        helper.createDatabase(DB, 1).apply {
            execSQL("INSERT INTO habits (id, name, description, enabled, created_at) VALUES ('h1', 'Scrolling', NULL, 1, 1)")
            execSQL(
                "INSERT INTO habit_events (id, type, package_name, habit_id, timestamp, metadata) " +
                    "VALUES ('e1', 'APP_BLOCKED', 'com.a', 'h1', 5, '{}')",
            )
            execSQL(
                "INSERT INTO settings (id, onboarding_done, theme_mode, dynamic_color, notify_focus, notify_guard, notify_summary, " +
                    "bypass_minor, bypass_major, bypass_max, focus_dnd_enabled, notification_permission_asked, user_name, user_goal, user_reason, avatar_shape) " +
                    "VALUES (0, 1, 'DARK', 0, 1, 1, 1, 1, 0, 0, 1, 0, 'Ayaan', NULL, 'control', 2)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB, 2, true)

        db.query("SELECT name FROM habits WHERE id = 'h1'").use { assertEquals("Scrolling", it.apply { moveToFirst() }.getString(0)) }
        db.query("SELECT COUNT(*) FROM habit_events").use { assertEquals(1, it.apply { moveToFirst() }.getInt(0)) }
        db.query("SELECT user_name, theme_mode, updates_enabled, update_channel, update_snoozed_until, update_last_checked FROM settings WHERE id = 0").use {
            it.moveToFirst()
            assertEquals("Ayaan", it.getString(0))
            assertEquals("DARK", it.getString(1))
            assertEquals(1, it.getInt(2)) // default: updates on
            assertEquals(true, it.isNull(3))
            assertEquals(0L, it.getLong(4))
            assertEquals(0L, it.getLong(5))
        }
    }

    @Test
    fun migrate2To3_addsEscalationOffByDefault() {
        helper.createDatabase(DB, 2).apply {
            execSQL("INSERT INTO habits (id, name, description, enabled, created_at) VALUES ('h1', 'Scrolling', NULL, 1, 1)")
            execSQL("INSERT INTO restriction_rules (id, habit_id, daily_limit_minutes, allowed_start_minutes, allowed_end_minutes, max_launches, warning_level, pause_seconds) VALUES ('r1', 'h1', 30, NULL, NULL, NULL, 'MAX', 5)")
            close()
        }

        val db = helper.runMigrationsAndValidate(DB, 3, true)

        db.query("SELECT daily_limit_minutes, escalation_enabled, escalation_major_minutes, escalation_max_minutes FROM restriction_rules WHERE habit_id = 'h1'").use {
            it.moveToFirst()
            assertEquals(30, it.getInt(0))
            assertEquals(0, it.getInt(1)) // existing rules keep escalation off
            assertEquals(20, it.getInt(2))
            assertEquals(40, it.getInt(3))
        }
    }

    private companion object {
        const val DB = "migration-test"
    }
}
