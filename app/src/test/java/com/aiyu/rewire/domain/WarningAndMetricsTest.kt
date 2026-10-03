package com.aiyu.rewire.domain

import com.aiyu.rewire.data.DbWriter
import com.aiyu.rewire.data.RoomWarningRepository
import com.aiyu.rewire.domain.analytics.HabitEvent
import com.aiyu.rewire.domain.analytics.HabitEventType
import com.aiyu.rewire.domain.analytics.MetricsCalculator
import com.aiyu.rewire.domain.analytics.Punchlines
import com.aiyu.rewire.domain.habit.WarningLevel
import com.aiyu.rewire.domain.warning.Warning
import com.aiyu.rewire.domain.warning.WarningCategory
import com.aiyu.rewire.domain.warning.WarningPicker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import com.aiyu.rewire.data.local.WarningDao
import com.aiyu.rewire.data.local.WarningEntity
import com.aiyu.rewire.data.local.toEntity
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random

class WarningAndMetricsTest {
    private fun w(id: String, level: WarningLevel, enabled: Boolean = true) =
        Warning(id, WarningCategory.DISCIPLINE, level, "t", "m", "mm", enabled)

    @Test fun picksMatchingLevelOnly() {
        val list = listOf(w("a", WarningLevel.MINOR), w("b", WarningLevel.MAX))
        repeat(20) { assertEquals("b", WarningPicker.pick(list, WarningLevel.MAX, Random(it))!!.id) }
    }

    @Test fun skipsDisabledAndFallsBackToAnyEnabled() {
        val list = listOf(w("a", WarningLevel.MAJOR, enabled = false), w("b", WarningLevel.MINOR))
        assertEquals("b", WarningPicker.pick(list, WarningLevel.MAJOR)!!.id)
    }

    @Test fun nothingEnabledReturnsNull() =
        assertNull(WarningPicker.pick(listOf(w("a", WarningLevel.MINOR, enabled = false)), WarningLevel.MINOR))

    @Test fun dailyMetricsAggregatesOnlyThatDay() {
        val day = LocalDate.of(2026, 10, 1)
        val t = day.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val events = listOf(
            HabitEvent("1", HabitEventType.BREAK_STARTED, null, null, t + 1000, mapOf(HabitEvent.KEY_FOCUS_MINUTES to "25")),
            HabitEvent("2", HabitEventType.FOCUS_COMPLETED, null, null, t + 2000, mapOf(HabitEvent.KEY_FOCUS_MINUTES to "25")),
            HabitEvent("3", HabitEventType.WARNING_SHOWN, "p", "h", t + 3000),
            HabitEvent("4", HabitEventType.OVERRIDE_USED, "p", "h", t + 4000),
            HabitEvent("5", HabitEventType.APP_BLOCKED, "p", "h", t + 5000),
            HabitEvent("6", HabitEventType.WARNING_SHOWN, "p", "h", t - 1000), // previous day
        )
        val m = MetricsCalculator.daily(events, day, ZoneOffset.UTC)
        assertEquals(50, m.focusMinutes)
        assertEquals(1, m.warningCount)
        assertEquals(1, m.blockedAttempts)
        assertEquals(0.5f, m.disciplineScore!!, 0.001f)
    }

    @Test fun newDefaultsMergeIntoExistingLibraryKeepingEdits() {
        val dao = FakeWarningDao(listOf(w("a", WarningLevel.MINOR, enabled = false).toEntity()))
        val repo = RoomWarningRepository(dao, DbWriter(CoroutineScope(Dispatchers.Unconfined)), listOf(w("a", WarningLevel.MINOR), w("b", WarningLevel.MAX)))
        assertEquals(listOf("a", "b"), repo.warnings.value.map { it.id })
        assertEquals(false, repo.warnings.value.first().enabled) // user's edit kept
    }

    @Test fun unedited_builtins_read_in_the_current_language_and_room_keeps_english() {
        val en = w("a", WarningLevel.MINOR).copy(title = "Hello")
        val hi = en.copy(title = "Namaste")
        val dao = FakeWarningDao(emptyList())
        var lang = hi
        val repo = RoomWarningRepository(dao, DbWriter(CoroutineScope(Dispatchers.Unconfined)), listOf(en)) { listOf(lang) }
        assertEquals("Namaste", repo.warnings.value.single().title)

        repo.update(repo.warnings.value.single().copy(favorite = true)) // not a rewording
        assertEquals("Hello", dao.rows.single().title) // English baseline stored, so it can re-localize
        lang = en.copy(title = "Bonjour"); repo.relocalize()
        assertEquals("Bonjour", repo.warnings.value.single().title)

        repo.update(repo.warnings.value.single().copy(title = "Mine")) // user's own wording wins in every language
        lang = hi; repo.relocalize()
        assertEquals("Mine", repo.warnings.value.single().title)
    }

    @Test fun bundledDefaultsParseWithUniqueIds() {
        val list = RoomWarningRepository.defaults(File("src/main/res/raw/default_warnings.json").readText())
        assertEquals(list.size, list.map { it.id }.toSet().size)
        WarningLevel.entries.forEach { l -> assert(list.count { it.level == l } >= 5) }
    }

    @Test fun breakdownGroupsGuardEventsByKeySinceCutoff() {
        val t = 1_000_000L
        val events = listOf(
            HabitEvent("1", HabitEventType.WARNING_SHOWN, "insta", "doom", t),
            HabitEvent("2", HabitEventType.WENT_BACK, "insta", "doom", t),
            HabitEvent("3", HabitEventType.APP_BLOCKED, "steam", "gaming", t),
            HabitEvent("4", HabitEventType.OVERRIDE_USED, "steam", "gaming", t),
            HabitEvent("5", HabitEventType.APP_BLOCKED, "steam", "gaming", t),
            HabitEvent("6", HabitEventType.WARNING_SHOWN, "insta", "doom", t - 1), // before cutoff
            HabitEvent("7", HabitEventType.FOCUS_COMPLETED, null, null, t), // not guard
        )
        val b = MetricsCalculator.breakdown(events, t) { it.habitId }
        assertEquals(listOf("gaming", "doom"), b.map { it.key })
        assertEquals(2, b[0].moments); assertEquals(1, b[0].overrides)
        assertEquals(1, b[1].moments); assertEquals(1, b[1].wentBack)
    }

    @Test fun peakHourPicksBusiestHour() {
        val base = LocalDate.of(2026, 10, 1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val h = 3_600_000L
        val events = listOf(22, 22, 9).mapIndexed { i, hr -> HabitEvent("$i", HabitEventType.WARNING_SHOWN, "p", "h", base + hr * h) }
        assertEquals(22, MetricsCalculator.peakHour(events, 0, ZoneOffset.UTC))
        assertNull(MetricsCalculator.peakHour(emptyList(), 0, ZoneOffset.UTC))
    }

    @Test fun punchlinesNullWithoutDataAndStableForSeed() {
        assertNull(Punchlines.focus(0, 1))
        assertNull(Punchlines.guard(0, 0, 1))
        assertEquals(Punchlines.focus(300, 7), Punchlines.focus(300, 7))
        assertNull(Punchlines.focus(2, 0)!!.unit)
        repeat(10) { assert(Punchlines.focus(30, it.toLong())!!.unit != null) }
    }
}

/** Mirrors Room's insert-ignore: existing ids keep the user's row. */
private class FakeWarningDao(initial: List<WarningEntity>) : WarningDao {
    val rows = initial.toMutableList()
    override suspend fun all() = rows.toList()
    override suspend fun insertMissing(warnings: List<WarningEntity>) { warnings.filter { w -> rows.none { it.id == w.id } }.forEach { rows += it } }
    override suspend fun update(warning: WarningEntity) { rows.replaceAll { if (it.id == warning.id) warning else it } }
    override suspend fun deleteCustom(id: String) { rows.removeAll { it.id == id && it.custom } }
    override suspend fun deleteAll() { rows.clear() }
}
