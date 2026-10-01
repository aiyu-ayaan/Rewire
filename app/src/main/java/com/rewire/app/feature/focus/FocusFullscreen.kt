package com.rewire.app.feature.focus

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewire.app.core.notifications.RewireNotifier
import com.rewire.app.domain.focus.FocusSessionStatus
import com.rewire.app.rewireViewModel
import com.rewire.app.ui.components.sharedBoundsOrSelf
import com.rewire.app.ui.theme.DarkColors
import com.rewire.app.ui.theme.TimerTextStyle
import com.rewire.app.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlin.random.Random

const val TIMER_KEY = "focus-timer"

/** One focus timer for the whole activity: tab screen and fullscreen route share it. */
@Composable
fun focusViewModel(): FocusViewModel =
    rewireViewModel(owner = LocalActivity.current as ComponentActivity) { FocusViewModel(it) }

/** True-black scheme: AMOLED pixels off everywhere except the digits and thin progress. */
private val AmoledColors = DarkColors.copy(
    background = Color.Black, surface = Color.Black,
    surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF111111), surfaceContainerHigh = Color(0xFF161616), surfaceContainerHighest = Color(0xFF1C1C1C),
)

@Composable
fun FocusFullscreenScreen(onExit: () -> Unit) {
    val vm = focusViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()

    // Session ended (completed / cancelled elsewhere) -> fall back to the normal screen, which shows the result.
    LaunchedEffect(state.isActive) { if (!state.isActive) onExit() }
    BackHandler(onBack = onExit)
    ImmersiveKeepScreenOn()

    val paused = state.status == FocusSessionStatus.PAUSED
    val onBreak = state.phase == FocusSessionStatus.BREAK
    var controlsVisible by rememberSaveable { mutableStateOf(false) }
    var interaction by remember { mutableIntStateOf(0) }
    // Controls auto-hide; every tap/action restarts the timer.
    LaunchedEffect(controlsVisible, interaction) { if (controlsVisible && !paused) { delay(4_000); controlsVisible = false } }

    MaterialTheme(colorScheme = AmoledColors) {
        val c = MaterialTheme.colorScheme
        val accent = if (onBreak) c.tertiary else c.primary
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(remember { MutableInteractionSource() }, indication = null) { controlsVisible = !controlsVisible; interaction++ }
                .safeDrawingPadding(),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize().burnInDrift(), contentAlignment = Alignment.Center) {
                // Digits fill the short side; works portrait + landscape.
                val digitSize = min(maxWidth / 3.4f, maxHeight / 1.9f)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedContent(
                        targetState = when { paused -> "Paused"; onBreak -> "Break"; else -> "Deep work" },
                        transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.8f)).togetherWith(fadeOut() + scaleOut(targetScale = 1.1f)) },
                        label = "phase",
                    ) { label ->
                        Text(label.uppercase(), style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 4.sp), color = accent.copy(alpha = 0.8f))
                    }
                    Spacer(Modifier.height(8.dp))
                    RollingTime(
                        millis = state.remaining(now),
                        style = with(LocalDensity.current) { TimerTextStyle.copy(fontSize = digitSize.toSp(), lineHeight = (digitSize * 1.15f).toSp()) },
                        color = if (paused) c.onSurfaceVariant else c.onSurface,
                        blinkColon = !paused,
                        modifier = Modifier.sharedBoundsOrSelf(TIMER_KEY),
                    )
                    LinearWavyProgressIndicator(
                        progress = { state.progress(now) },
                        color = accent,
                        trackColor = c.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth(0.55f).padding(top = 12.dp),
                    )
                    Text(
                        "Session ${state.cycle} / ${state.config.cycles}",
                        style = MaterialTheme.typography.titleSmall,
                        color = c.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }

            AnimatedVisibility(
                visible = controlsVisible || paused,
                enter = fadeIn() + slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it / 2 },
                exit = fadeOut() + slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it / 2 },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = onExit, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(64.dp)) {
                        Icon(Icons.Rounded.FullscreenExit, contentDescription = "Exit full screen")
                    }
                    FilledIconButton(
                        onClick = { if (paused) vm.resume() else vm.pause(); interaction++ },
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier.size(84.dp),
                    ) {
                        Icon(if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, contentDescription = if (paused) "Resume" else "Pause", modifier = Modifier.size(36.dp))
                    }
                    Box(Modifier.size(64.dp)) {
                        if (onBreak) FilledTonalIconButton(onClick = { vm.skipBreak(); interaction++ }, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(64.dp)) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = "Skip break")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Countdown digits that roll individually: new digit drops in from above with a bouncy spring,
 * old one falls away. Only changed digits move, so each second is one small, readable motion.
 */
@Composable
fun RollingTime(
    millis: Long,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    blinkColon: Boolean = false,
) {
    val text = RewireNotifier.formatRemaining(millis)
    val reduced = rememberReducedMotion()
    val bounce = spring<IntOffset>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
    val colonAlpha by animateFloatAsState(
        if (blinkColon && (millis / 1000) % 2 == 0L) 0.35f else 1f, tween(300), label = "colon",
    )
    Row(
        modifier.semantics { contentDescription = "$text remaining"; liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        text.forEachIndexed { i, ch ->
            // Keyed by position from the right so "10:00" -> "9:59" keeps the seconds slots stable.
            key(text.length - i) {
            if (ch == ':') {
                Text(":", style = style, color = color, modifier = Modifier.alpha(colonAlpha))
            } else {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        if (reduced) EnterTransition.None togetherWith ExitTransition.None
                        else (slideInVertically(bounce) { -it } + fadeIn(tween(150)))
                            .togetherWith(slideOutVertically(bounce) { it / 2 } + fadeOut(tween(120)))
                            .using(SizeTransform(clip = false))
                    },
                    label = "digit${text.length - i}",
                ) { d -> Text(d.toString(), style = style, color = color) }
            }
            }
        }
    }
}

/** Hide status + nav bars (swipe to peek) and keep the display on while this is shown. */
@Composable
private fun ImmersiveKeepScreenOn() {
    val view = LocalView.current
    val activity = LocalActivity.current ?: return
    DisposableEffect(view) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = false
        }
    }
}

/** AMOLED burn-in guard: content drifts a few dp to a new spot every minute, softly. */
@Composable
private fun Modifier.burnInDrift(): Modifier {
    var target by remember { mutableStateOf(0 to 0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            target = Random.nextInt(-12, 13) to Random.nextInt(-16, 17)
        }
    }
    val spec = spring<androidx.compose.ui.unit.Dp>(stiffness = Spring.StiffnessVeryLow)
    val x by animateDpAsState(target.first.dp, spec, label = "dx")
    val y by animateDpAsState(target.second.dp, spec, label = "dy")
    return this.offset(x, y)
}
