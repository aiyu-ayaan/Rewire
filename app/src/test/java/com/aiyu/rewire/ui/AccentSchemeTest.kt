package com.aiyu.rewire.ui

import com.aiyu.rewire.ui.theme.Accent
import com.aiyu.rewire.ui.theme.DarkColors
import com.aiyu.rewire.ui.theme.FocusDarkColors
import com.aiyu.rewire.ui.theme.FocusLightColors
import com.aiyu.rewire.ui.theme.LightColors
import com.aiyu.rewire.ui.theme.MatrixLightColors
import com.aiyu.rewire.ui.theme.QuitLightColors
import com.aiyu.rewire.ui.theme.accentScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AccentSchemeTest {

    @Test fun brandKeepsTheTealSchemes() {
        assertSame(LightColors, accentScheme(Accent.BRAND, dark = false, dynamic = null))
        assertSame(DarkColors, accentScheme(Accent.BRAND, dark = true, dynamic = null))
    }

    @Test fun eachTabGetsItsOwnHueInBothModes() {
        assertSame(FocusLightColors, accentScheme(Accent.FOCUS, dark = false, dynamic = null))
        assertSame(FocusDarkColors, accentScheme(Accent.FOCUS, dark = true, dynamic = null))
        val primaries = Accent.entries.map { accentScheme(it, dark = false, dynamic = null).primary }
        assertEquals(primaries.size, primaries.toSet().size)
    }

    @Test fun warningLevelColorsStayBrandOnEveryTab() {
        for (s in listOf(FocusLightColors, QuitLightColors, MatrixLightColors)) {
            assertEquals(LightColors.tertiaryContainer, s.tertiaryContainer)
            assertEquals(LightColors.errorContainer, s.errorContainer)
        }
    }

    @Test fun dynamicSwapsWhichRoleLeads() {
        val wall = LightColors // stands in for a wallpaper scheme
        assertSame(wall, accentScheme(Accent.BRAND, dark = false, dynamic = wall))
        val focus = accentScheme(Accent.FOCUS, dark = false, dynamic = wall)
        assertEquals(wall.secondary, focus.primary)
        assertEquals(wall.primary, focus.secondary)
        assertEquals(wall.onSecondaryContainer, focus.onPrimaryContainer)
        val matrix = accentScheme(Accent.MATRIX, dark = false, dynamic = wall)
        assertEquals(wall.tertiary, matrix.primary)
        assertEquals(wall.primaryContainer, matrix.tertiaryContainer)
        assertNotEquals(focus.primary, matrix.primary)
    }
}
