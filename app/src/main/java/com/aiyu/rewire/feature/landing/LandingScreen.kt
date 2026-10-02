package com.aiyu.rewire.feature.landing

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon
import com.aiyu.rewire.R
import com.aiyu.rewire.ui.components.MorphingShape
import com.aiyu.rewire.ui.components.heroBrush
import com.aiyu.rewire.ui.components.sharedBoundsOrSelf
import com.aiyu.rewire.ui.theme.RewireMotion
import com.aiyu.rewire.ui.theme.rememberReducedMotion
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

const val HERO_KEY = "rewire-hero"

private data class StoryPage(val word: String, val body: String)

private val story = listOf(
    StoryPage("Friction", "Rewire adds a small pause before the apps that pull you in."),
    StoryPage("Awareness", "Each pause asks one question: is this a choice or a reflex?"),
    StoryPage("Choice", "You decide. Rewire measures, so you can see yourself improve."),
)

@Composable
fun LandingScreen(onGetStarted: () -> Unit) {
    val pager = rememberPagerState { story.size }
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val last = pager.currentPage == story.lastIndex

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(colors.primaryContainer.copy(alpha = 0.55f), colors.surface, colors.surface)))
    ) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "REWIRE",
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 6.sp),
                color = colors.primary,
                modifier = Modifier.padding(top = 24.dp),
            )
            // Hero: orbiting shapes around a continuously morphing blob.
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val heroSize = minOf(maxWidth, maxHeight) * 0.62f
                Orbit(Modifier.size(heroSize * 1.55f))
                MorphingShape(
                    brush = heroBrush(),
                    modifier = Modifier
                        .size(heroSize)
                        .sharedBoundsOrSelf(HERO_KEY)
                        .semantics { contentDescription = "Rewire" },
                )
            }
            Text(
                stringResource(R.string.tagline),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(16.dp))
            HorizontalPager(pager, Modifier.fillMaxWidth().heightIn(min = 96.dp)) { page ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(story[page].word, style = MaterialTheme.typography.titleLarge, color = colors.primary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        story[page].body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 360.dp),
                    )
                }
            }
            PageDots(count = story.size, current = pager.currentPage)
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { if (last) onGetStarted() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp).height(ButtonDefaults.MediumContainerHeight),
                contentPadding = ButtonDefaults.MediumContentPadding,
            ) {
                Text(if (last) "Get started" else "Next", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.size(8.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
            }
            TextButton(onClick = onGetStarted, modifier = Modifier.alpha(if (last) 0f else 1f), enabled = !last) {
                Text("Skip")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == current) 28.dp else 8.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "dot")
            Box(
                Modifier
                    .height(8.dp)
                    .size(width = width, height = 8.dp)
                    .clip(CircleShape)
                    .background(if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
            )
        }
    }
}

/** Small shapes on a slow orbit, each spinning on its own axis. Static under reduced motion. */
@Composable
private fun Orbit(modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    val satellites: List<Pair<RoundedPolygon, androidx.compose.ui.graphics.Color>> = listOf(
        MaterialShapes.Pill to c.secondaryContainer,
        MaterialShapes.Sunny to c.tertiaryContainer,
        MaterialShapes.Gem to c.primaryContainer,
        MaterialShapes.Clover8Leaf to c.secondary.copy(alpha = 0.35f),
        MaterialShapes.Triangle to c.tertiary.copy(alpha = 0.35f),
    )
    val live = !rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "orbit")
    val angle by transition.animateFloat(0f, if (live) 360f else 0f, infiniteRepeatable(tween(RewireMotion.ORBIT_MILLIS, easing = LinearEasing)), label = "a")
    val breathe by transition.animateFloat(0.92f, if (live) 1.08f else 0.92f, infiniteRepeatable(tween(RewireMotion.BREATHE_MILLIS), RepeatMode.Reverse), label = "b")

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val radius = maxWidth / 2 * 0.86f
        satellites.forEachIndexed { i, (shape, color) ->
            val theta = Math.toRadians((angle + i * 360f / satellites.size).toDouble())
            val s = (34 + (i % 3) * 10).dp
            MorphingShape(
                brush = SolidColor(color),
                shapes = listOf(shape),
                rotationMillis = 6000 + i * 1500,
                modifier = Modifier
                    .offset(x = radius * cos(theta).toFloat(), y = radius * sin(theta).toFloat())
                    .size(s)
                    .aspectRatio(1f)
                    .graphicsLayer { scaleX = breathe; scaleY = breathe },
            )
        }
    }
}
