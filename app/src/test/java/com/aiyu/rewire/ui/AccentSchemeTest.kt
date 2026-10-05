package com.aiyu.rewire.ui

import com.aiyu.rewire.ui.theme.Accent
import com.aiyu.rewire.ui.theme.DarkColors
import com.aiyu.rewire.ui.theme.DynamicSchemes
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

    // DarkColors / LightColors stand in for the wallpaper's two schemes.
    private val wall = DynamicSchemes(light = LightColors, dark = DarkColors)

    @Test fun dynamicSwapsWhichRoleLeads() {
        assertSame(LightColors, accentScheme(Accent.BRAND, dark = false, dynamic = wall))
        assertSame(DarkColors, accentScheme(Accent.QUIT, dark = true, dynamic = wall))
        val focus = accentScheme(Accent.FOCUS, dark = false, dynamic = wall)
        assertEquals(LightColors.secondary, focus.primary)
        assertEquals(LightColors.onSecondaryContainer, focus.onPrimaryContainer)
        assertEquals(LightColors.secondaryContainer, focus.secondaryContainer)
        val matrix = accentScheme(Accent.MATRIX, dark = false, dynamic = wall)
        assertEquals(LightColors.tertiary, matrix.primary)
        // Containers follow the lead hue (no wallpaper-primary chips on Matrix)...
        assertEquals(LightColors.tertiaryContainer, matrix.secondaryContainer)
        assertEquals(LightColors.onTertiaryContainer, matrix.onSecondaryContainer)
        assertEquals(LightColors.primaryContainer, matrix.tertiaryContainer)
        assertNotEquals(focus.primary, matrix.primary)
    }

    @Test fun dynamicChartSeriesStayDistinct() {
        for (dark in listOf(false, true)) {
            val m = accentScheme(Accent.MATRIX, dark, wall)
            val series = listOf(m.primary, m.secondary, m.tertiary, m.error)
            assertEquals(series.size, series.toSet().size)
        }
    }

    @Test fun dynamicGradientStaysInTheLeadHue() {
        // heroBrush = primary -> inversePrimary; inverse is the lead role's tone from the other mode.
        assertEquals(DarkColors.secondary, accentScheme(Accent.FOCUS, dark = false, dynamic = wall).inversePrimary)
        assertEquals(LightColors.tertiary, accentScheme(Accent.MATRIX, dark = true, dynamic = wall).inversePrimary)
    }
}
