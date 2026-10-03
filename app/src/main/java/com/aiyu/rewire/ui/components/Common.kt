package com.aiyu.rewire.ui.components

import androidx.compose.ui.semantics.heading

import androidx.compose.ui.semantics.semantics

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.ui.res.stringResource
import com.aiyu.rewire.R
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aiyu.rewire.core.apps.rememberAppIcon
import com.aiyu.rewire.domain.habit.WarningLevel

// ---- Shared transition plumbing -----------------------------------------------------------------

val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Shared-bounds when inside the nav shared scope; no-op otherwise (previews, tests). */
@Composable
fun Modifier.sharedBoundsOrSelf(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val anim = LocalNavAnimatedScope.current ?: return this
    return with(shared) {
        this@sharedBoundsOrSelf.sharedBounds(
            rememberSharedContentState(key),
            animatedVisibilityScope = anim,
            // Slightly soft spring so the eye can follow the element between screens.
            boundsTransform = { _, _ -> spring(dampingRatio = 0.85f, stiffness = 260f) },
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
        )
    }
}

// ---- Level visuals (never color only: icon + label always present) -----------------------------

data class LevelStyle(val container: Color, val onContainer: Color, val accent: Color, val icon: ImageVector, val label: String, val summary: String)

@Composable
fun WarningLevel.style(): LevelStyle {
    val c = MaterialTheme.colorScheme
    return when (this) {
        WarningLevel.MINOR -> LevelStyle(c.secondaryContainer, c.onSecondaryContainer, c.secondary, Icons.Rounded.Lightbulb, stringResource(R.string.level_minor), stringResource(R.string.level_minor_summary))
        WarningLevel.MAJOR -> LevelStyle(c.tertiaryContainer, c.onTertiaryContainer, c.tertiary, Icons.Rounded.PanTool, stringResource(R.string.level_major), stringResource(R.string.level_major_summary))
        WarningLevel.MAX -> LevelStyle(c.errorContainer, c.onErrorContainer, c.error, Icons.Rounded.Block, stringResource(R.string.level_max), stringResource(R.string.level_max_summary))
    }
}

@Composable
fun LevelBadge(level: WarningLevel, modifier: Modifier = Modifier) {
    val s = level.style()
    Surface(color = s.container, contentColor = s.onContainer, shape = CircleShape, modifier = modifier) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(s.icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(6.dp))
            Text(s.label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

// ---- Misc ---------------------------------------------------------------------------------------

@Composable
fun AppIcon(packageName: String, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val icon = rememberAppIcon(packageName)
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(size))
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f).semantics { heading() })
        trailing?.invoke()
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MorphingShape(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier.size(120.dp),
            rotationMillis = 30_000,
        )
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(8.dp)); action() }
    }
}

fun formatMinutes(total: Int): String = when {
    total < 60 -> "${total}m"
    total % 60 == 0 -> "${total / 60}h"
    else -> "${total / 60}h ${total % 60}m"
}

fun formatClock(minutesOfDay: Int): String = "%02d:%02d".format(minutesOfDay / 60, minutesOfDay % 60)

/** Caps line length on wide windows (tablets, foldables, landscape): centered, never wider than [max]. */
fun Modifier.readableWidth(max: Dp = 640.dp): Modifier =
    fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = max).fillMaxWidth()

/** True once the user's font scale is large enough that side-by-side layouts get too narrow for text. */
@Composable
fun isLargeFont(): Boolean = LocalDensity.current.fontScale > 1.3f

/**
 * [leading] (ring, donut) next to [trailing]; stacks vertically at large font scale so text keeps
 * its width. [trailing] receives the modifier it must apply (weight in the row, full width stacked).
 */
@Composable
fun BesideOrStacked(leading: @Composable () -> Unit, trailing: @Composable (Modifier) -> Unit) {
    if (isLargeFont()) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            leading()
            trailing(Modifier.fillMaxWidth())
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            leading()
            Spacer(Modifier.width(20.dp))
            trailing(Modifier.weight(1f))
        }
    }
}

/** Big timer digits would overflow their ring at 200% text; cap the scale for them only (the rest still scales). */
@Composable
fun CappedFontScale(max: Float = 1.3f, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale.coerceAtMost(max)), content = content)
}

/**
 * Two side-by-side columns when the space is wide (tablets, unfolded foldables, landscape), one stacked
 * column otherwise. Decided from the real available width, so a fold/unfold or split-screen re-lays out live.
 */
@Composable
fun AdaptiveColumns(
    modifier: Modifier = Modifier,
    first: @Composable ColumnScope.() -> Unit,
    second: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth >= TWO_PANE_MIN_WIDTH) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), content = first)
                Column(Modifier.weight(1f), content = second)
            }
        } else {
            Column { first(); second() }
        }
    }
}

/** Widest a single column may get before a screen should split in two. */
val TWO_PANE_MIN_WIDTH = 680.dp

/** Outer width cap for screens built from [AdaptiveColumns]. */
val TWO_PANE_MAX_WIDTH = 1200.dp
