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
import androidx.compose.ui.platform.LocalContext
import com.aiyu.rewire.core.settings.ThemeMode

/** Screen color: Guard and Profile wear the brand teal, Focus, Quit and Matrix their own hue. */
enum class Accent { BRAND, FOCUS, QUIT, MATRIX }

/** What [AccentTheme] derives from: the app's light/dark choice and, with dynamic color on, the wallpaper scheme. */
private data class ThemeBase(val dark: Boolean, val dynamic: ColorScheme?)

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
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
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

/**
 * Brand palettes per accent. With dynamic color ([dynamic] non-null) the wallpaper gives one palette only,
 * so tabs swap which of its roles leads: Focus leads with secondary, Matrix with tertiary.
 */
fun accentScheme(accent: Accent, dark: Boolean, dynamic: ColorScheme?): ColorScheme = when {
    dynamic != null -> when (accent) {
        Accent.BRAND, Accent.QUIT -> dynamic
        Accent.FOCUS -> dynamic.leadWithSecondary()
        Accent.MATRIX -> dynamic.leadWithTertiary()
    }
    else -> when (accent) {
        Accent.BRAND -> if (dark) DarkColors else LightColors
        Accent.FOCUS -> if (dark) FocusDarkColors else FocusLightColors
        Accent.QUIT -> if (dark) QuitDarkColors else QuitLightColors
        Accent.MATRIX -> if (dark) MatrixDarkColors else MatrixLightColors
    }
}

private fun ColorScheme.leadWithSecondary() = copy(
    primary = secondary, onPrimary = onSecondary,
    primaryContainer = secondaryContainer, onPrimaryContainer = onSecondaryContainer,
    secondary = primary, onSecondary = onPrimary,
    secondaryContainer = primaryContainer, onSecondaryContainer = onPrimaryContainer,
    surfaceTint = secondary,
)

private fun ColorScheme.leadWithTertiary() = copy(
    primary = tertiary, onPrimary = onTertiary,
    primaryContainer = tertiaryContainer, onPrimaryContainer = onTertiaryContainer,
    tertiary = primary, onTertiary = onPrimary,
    tertiaryContainer = primaryContainer, onTertiaryContainer = onPrimaryContainer,
    surfaceTint = tertiary,
)
