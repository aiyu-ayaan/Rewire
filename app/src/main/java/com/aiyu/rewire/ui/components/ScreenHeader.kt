package com.aiyu.rewire.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aiyu.rewire.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay

/**
 * Tab top bar: an animated mark, the title and a subtitle, with optional actions at the end.
 * Each tab passes its own [mark]; its motion says what the page does (guard watches, focus ticks, quit breathes...).
 */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String,
    mark: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        mark(Modifier.size(44.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        actions()
    }
}

/** Steps 30° forward once a second with a small overshoot, like a clock tick. */
@Composable
fun Modifier.ticking(): Modifier {
    if (rememberReducedMotion()) return this
    val angle = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            angle.animateTo(angle.value + 30f, spring(dampingRatio = 0.45f, stiffness = 500f))
        }
    }
    return graphicsLayer { rotationZ = angle.value }
}

/** Slow paced breath: in for [inMillis], hold for [holdMillis], out for [outMillis]. */
@Composable
fun Modifier.breathing(inMillis: Int, holdMillis: Int, outMillis: Int): Modifier {
    if (rememberReducedMotion()) return this
    val total = inMillis + holdMillis + outMillis
    val scale by rememberInfiniteTransition(label = "breath").animateFloat(
        0.72f, 0.72f,
        infiniteRepeatable(keyframes {
            durationMillis = total
            1f at inMillis
            1f at inMillis + holdMillis
        }),
        label = "scale",
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}
