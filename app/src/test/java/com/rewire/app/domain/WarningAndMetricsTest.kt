package com.rewire.app.domain

import com.rewire.app.data.JsonStore
import com.rewire.app.data.PersistentWarningRepository
import com.rewire.app.domain.analytics.HabitEvent
import com.rewire.app.domain.analytics.HabitEventType
import com.rewire.app.domain.analytics.MetricsCalculator
import com.rewire.app.domain.habit.WarningLevel
import com.rewire.app.domain.warning.Warning
import com.rewire.app.domain.warning.WarningCategory
import com.rewire.app.domain.warning.WarningPicker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.builtins.ListSerializer
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
        val file = File.createTempFile("warnings", ".json").apply { delete() }
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val store = JsonStore(file, ListSerializer(Warning.serializer()), scope) { listOf(w("a", WarningLevel.MINOR, enabled = false)) }
        val repo = PersistentWarningRepository(store, listOf(w("a", WarningLevel.MINOR), w("b", WarningLevel.MAX)))
        assertEquals(listOf("a", "b"), repo.warnings.value.map { it.id })
        assertEquals(false, repo.warnings.value.first().enabled) // user's edit kept
    }

    @Test fun bundledDefaultsParseWithUniqueIds() {
        val list = PersistentWarningRepository.defaults(File("src/main/res/raw/default_warnings.json").readText())
        assertEquals(list.size, list.map { it.id }.toSet().size)
        WarningLevel.entries.forEach { l -> assert(list.count { it.level == l } >= 5) }
    }
}
