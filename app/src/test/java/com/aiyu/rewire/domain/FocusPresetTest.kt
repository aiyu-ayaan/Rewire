package com.aiyu.rewire.domain

import com.aiyu.rewire.domain.focus.FocusConfig
import com.aiyu.rewire.domain.focus.FocusPreset
import com.aiyu.rewire.domain.focus.FocusPresetError
import com.aiyu.rewire.domain.focus.FocusPresets
import com.aiyu.rewire.domain.focus.PresetResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusPresetTest {
    private val cfg = FocusConfig(30, 5, 2)
    private fun err(r: PresetResult) = (r as PresetResult.Err).error
    private fun ok(r: PresetResult) = (r as PresetResult.Ok).presets

    @Test fun builtInsAreAllValid() = assertTrue(FocusPresets.builtIn.all { it.toConfig().isValid })

    @Test fun addTrimsAndStores() {
        val l = ok(FocusPresets.add(emptyList(), "a", "  Mine ", cfg))
        assertEquals(FocusPreset("a", "Mine", 30, 5, 2), l.single())
    }

    @Test fun rejectsBreakLongerThanFocus() =
        assertEquals(FocusPresetError.INVALID_CONFIG, err(FocusPresets.add(emptyList(), "a", "X", FocusConfig(20, 30, 1))))

    @Test fun rejectsBlankAndLongNames() {
        assertEquals(FocusPresetError.NAME_BLANK, err(FocusPresets.add(emptyList(), "a", "  ", cfg)))
        assertEquals(FocusPresetError.NAME_TOO_LONG, err(FocusPresets.add(emptyList(), "a", "x".repeat(25), cfg)))
    }

    @Test fun rejectsDuplicateCaseInsensitiveAndBuiltInNames() {
        val l = ok(FocusPresets.add(emptyList(), "a", "Mine", cfg))
        assertEquals(FocusPresetError.NAME_TAKEN, err(FocusPresets.add(l, "b", " mine", cfg)))
        assertEquals(FocusPresetError.NAME_TAKEN, err(FocusPresets.add(l, "b", "pomodoro", cfg)))
    }

    @Test fun renameAllowsSameNameForSelfButNotOthers() {
        var l = ok(FocusPresets.add(emptyList(), "a", "One", cfg))
        l = ok(FocusPresets.add(l, "b", "Two", cfg))
        assertEquals("ONE", ok(FocusPresets.rename(l, "a", "ONE")).first().name)
        assertEquals(FocusPresetError.NAME_TAKEN, err(FocusPresets.rename(l, "a", "two")))
    }

    @Test fun capsUserPresets() {
        val l = (1..FocusPresets.MAX_USER_PRESETS).map { FocusPreset("i$it", "P$it", 25, 5, 1) }
        assertEquals(FocusPresetError.TOO_MANY, err(FocusPresets.add(l, "z", "Extra", cfg)))
    }

    @Test fun deleteRemoves() = assertTrue(FocusPresets.delete(listOf(FocusPreset("a", "A", 25, 5, 1)), "a").isEmpty())

    @Test fun sanitizeDropsInvalidAndDuplicates() {
        val good = FocusPreset("a", "A", 25, 5, 1)
        val out = FocusPresets.sanitize(listOf(good, good.copy(id = "b"), FocusPreset("c", "C", 10, 20, 1), FocusPreset("d", "", 25, 5, 1)))
        assertEquals(listOf(good), out)
    }
}
