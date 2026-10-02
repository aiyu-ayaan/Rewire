package com.aiyu.rewire.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import com.aiyu.rewire.ui.theme.rememberReducedMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** User-pickable avatar shapes. Index is what gets persisted. */
val AvatarShapes
    get() = listOf(
        MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Sunny, MaterialShapes.Gem,
        MaterialShapes.Puffy, MaterialShapes.Flower, MaterialShapes.SoftBurst, MaterialShapes.Cookie6Sided,
    )

/**
 * Shape avatar with the user's initial. Picking a new shape morphs into it with a bouncy spring
 * (and a small pop), so the choice feels physical.
 */
@Composable
fun UserAvatar(name: String, shapeIndex: Int, size: Dp, modifier: Modifier = Modifier) {
    val shapes = remember { AvatarShapes }
    val target = shapeIndex.coerceIn(shapes.indices)
    var from by remember { mutableIntStateOf(target) }
    var to by remember { mutableIntStateOf(target) }
    val progress = remember { Animatable(1f) }
    val pop = remember { Animatable(1f) }
    val reduced = rememberReducedMotion()
    LaunchedEffect(target) {
        if (target == to) return@LaunchedEffect
        from = to; to = target
        if (reduced) { progress.snapTo(1f); return@LaunchedEffect }
        progress.snapTo(0f)
        pop.snapTo(0.88f)
        coroutineScope {
            launch { progress.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) }
            launch { pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) }
        }
    }
    val morph = remember(from, to) { Morph(shapes[from], shapes[to]) }
    val brush = heroBrush()
    val path = remember { Path() }
    val matrix = remember { Matrix() }
    val initial = name.trim().firstOrNull()?.uppercase() ?: "•"

    Box(modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            path.rewind()
            morph.toPath(progress.value.coerceIn(0f, 1f), path) // bounce comes from the pop scale; extrapolated morphs distort
            matrix.reset(); matrix.scale(this.size.width, this.size.height)
            path.transform(matrix)
            drawPath(path, brush)
        }
        Text(
            initial,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.4f).sp,
        )
    }
}
