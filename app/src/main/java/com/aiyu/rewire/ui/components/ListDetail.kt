package com.aiyu.rewire.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp

/** Where each openable item currently sits on screen, so the detail pane can grow out of it. */
class PaneSources { val bounds = mutableMapOf<String, Rect>() }

val LocalPaneSources = staticCompositionLocalOf<PaneSources?> { null }

/** Marks the item that opens [key] in the detail pane. No-op outside a wide [ListDetail]. */
@Composable
fun Modifier.paneSource(key: String): Modifier {
    val sources = LocalPaneSources.current ?: return this
    DisposableEffect(sources, key) { onDispose { sources.bounds.remove(key) } }
    return onGloballyPositioned { sources.bounds[key] = Rect(it.positionInRoot(), it.size.toSize()) }
}

/**
 * Wide windows: [list] on the left (fixed width), the opened screen on the right like Android Settings.
 * Opening is a container transform: the pane grows out of the tapped item and shrinks back into it on close.
 * Items without a [paneSource] (or scrolled away) fade instead. [detail] null shows a quiet placeholder.
 */
@Composable
fun ListDetail(detail: String?, list: @Composable () -> Unit, listWidth: Dp = 420.dp, detailContent: @Composable (String) -> Unit) {
    val sources = remember { PaneSources() }
    var pane by remember { mutableStateOf(Rect.Zero) }
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val color = MaterialTheme.colorScheme.surfaceContainerLow
    val corner = 28.dp
    CompositionLocalProvider(LocalPaneSources provides sources) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(listWidth).fillMaxHeight()) { list() }
            Box(
                Modifier.weight(1f).fillMaxHeight().padding(top = 8.dp, end = 8.dp, bottom = 8.dp)
                    .onGloballyPositioned { pane = Rect(it.positionInRoot(), it.size.toSize()) },
            ) {
                // No size transform: it would clip the pane while it is still over the list.
                AnimatedContent(detail, transitionSpec = { EnterTransition.None togetherWith ExitTransition.None using null }, label = "pane") { key ->
                    // Custom animations on this transition are awaited before the outgoing pane is removed.
                    val p by transition.animateFloat({ spatial }, label = "container") { if (it == EnterExitState.Visible) 1f else 0f }
                    Surface(
                        Modifier.fillMaxSize().graphicsLayer {
                            val from = key?.let { sources.bounds[it] }?.translate(-pane.topLeft)
                            if (from == null || pane.isEmpty) {
                                alpha = p.coerceIn(0f, 1f)
                                scaleX = lerp(0.96f, 1f, p); scaleY = scaleX
                                clip = true; shape = rounded(size.width, size.height, corner.toPx())
                                return@graphicsLayer
                            }
                            // Bounds lerp from the item to the pane; content scales uniformly to the current width.
                            val w = lerp(from.width, size.width, p)
                            val k = w / size.width
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = k; scaleY = k
                            translationX = lerp(from.left, 0f, p)
                            translationY = lerp(from.top, 0f, p)
                            clip = true
                            shape = rounded(size.width, lerp(from.height, size.height, p) / k, corner.toPx() / k)
                            // Fade the container over the last stretch so it never sits opaque on top of the card (blink on close).
                            alpha = (p / 0.25f).coerceIn(0f, 1f)
                        },
                        color = color,
                    ) {
                        // Contents arrive once the container has mostly opened, like M3's fade-through.
                        Box(Modifier.graphicsLayer { alpha = ((p - 0.3f) / 0.7f).coerceIn(0f, 1f) }) {
                            // The pane is not a nav destination: hero/container transforms of nav screens don't apply here.
                            CompositionLocalProvider(LocalSharedTransitionScope provides null, LocalNavAnimatedScope provides null) {
                                if (key != null) detailContent(key)
                                else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    MorphingShape(brush = heroBrush(), modifier = Modifier.size(96.dp).alpha(0.4f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun rounded(width: Float, height: Float, radius: Float) = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        Outline.Rounded(RoundRect(0f, 0f, width, height, CornerRadius(radius)))
}
