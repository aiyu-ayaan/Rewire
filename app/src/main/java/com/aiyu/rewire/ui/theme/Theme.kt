package com.aiyu.rewire.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.aiyu.rewire.core.settings.ThemeMode

/** Screen color: Guard and Profile wear the brand teal, Focus, Quit and Matrix their own hue. */
enum class Accent { BRAND, FOCUS, QUIT, MATRIX }

/** The wallpaper's light and dark schemes. Both are kept: a role's inverse color is its tone in the other mode. */
data class DynamicSchemes(val light: ColorScheme, val dark: ColorScheme)

/** What [AccentTheme] derives from: the app's light/dark choice and, with dynamic color on, the wallpaper schemes. */
private data class ThemeBase(val dark: Boolean, val dynamic: DynamicSchemes?)

private val LocalThemeBase = staticCompositionLocalOf { ThemeBase(dark = false, dynamic = null) }

@Composable
fun RewireTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val dynamic = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        DynamicSchemes(dynamicLightColorScheme(context), dynamicDarkColorScheme(context))
    } else null
    CompositionLocalProvider(LocalThemeBase provides ThemeBase(dark, dynamic)) {
        MaterialExpressiveTheme(
            colorScheme = accentScheme(Accent.BRAND, dark, dynamic),
            motionScheme = MotionScheme.expressive(),
            shapes = RewireShapes,
            typography = RewireTypography,
            content = content,
        )
    }
}

/** Re-colors [content] for [accent]; shapes, type and motion stay the app's. Must sit inside [RewireTheme]. */
@Composable
fun AccentTheme(accent: Accent, content: @Composable () -> Unit) {
    val base = LocalThemeBase.current
    MaterialExpressiveTheme(
        colorScheme = remember(accent, base) { accentScheme(accent, base.dark, base.dynamic) },
        motionScheme = MaterialTheme.motionScheme,
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
        content = content,
    )
}

/** [accent]'s dark scheme whatever the app's mode, for always-dark screens (fullscreen focus). Wallpaper-based when dynamic color is on. */
@Composable
fun darkAccentScheme(accent: Accent): ColorScheme {
    val base = LocalThemeBase.current
    return remember(accent, base) { accentScheme(accent, dark = true, dynamic = base.dynamic) }
}

/**
 * Brand palettes per accent. With dynamic color ([dynamic] non-null) the wallpaper gives one palette only,
 * so tabs swap which of its roles leads: Focus leads with secondary, Matrix with tertiary.
 */
fun accentScheme(accent: Accent, dark: Boolean, dynamic: DynamicSchemes?): ColorScheme = when {
    dynamic != null -> {
        val (scheme, other) = if (dark) dynamic.dark to dynamic.light else dynamic.light to dynamic.dark
        when (accent) {
            Accent.BRAND, Accent.QUIT -> scheme
            Accent.FOCUS -> scheme.leadWithSecondary(inverse = other.secondary)
            Accent.MATRIX -> scheme.leadWithTertiary(inverse = other.tertiary)
        }
    }
    else -> when (accent) {
        Accent.BRAND -> if (dark) DarkColors else LightColors
        Accent.FOCUS -> if (dark) FocusDarkColors else FocusLightColors
        Accent.QUIT -> if (dark) QuitDarkColors else QuitLightColors
        Accent.MATRIX -> if (dark) MatrixDarkColors else MatrixLightColors
    }
}

// The lead palette takes over primary and the secondary containers (chips, selected segments, progress tracks), so
// a tab never mixes the wallpaper's primary into its own hue. Solid secondary/tertiary stay distinct: charts use them
// as separate series. [inverse] keeps gradients (primary -> inversePrimary) inside the lead hue.
private fun ColorScheme.leadWithSecondary(inverse: Color) = copy(
    primary = secondary, onPrimary = onSecondary,
    primaryContainer = secondaryContainer, onPrimaryContainer = onSecondaryContainer,
    inversePrimary = inverse,
    surfaceTint = secondary,
)

private fun ColorScheme.leadWithTertiary(inverse: Color) = copy(
    primary = tertiary, onPrimary = onTertiary,
    primaryContainer = tertiaryContainer, onPrimaryContainer = onTertiaryContainer,
    secondaryContainer = tertiaryContainer, onSecondaryContainer = onTertiaryContainer,
    tertiary = primary, onTertiary = onPrimary,
    tertiaryContainer = primaryContainer, onTertiaryContainer = onPrimaryContainer,
    inversePrimary = inverse,
    surfaceTint = tertiary,
)
