package com.rewire.app.feature.focus

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewire.app.core.datastore.FocusBypass
import com.rewire.app.core.notifications.NotificationRationaleCard
import com.rewire.app.core.notifications.RewireNotifier
import com.rewire.app.core.notifications.rememberNotificationPermission
import com.rewire.app.domain.focus.FocusConfig
import com.rewire.app.domain.focus.FocusConfigError
import com.rewire.app.domain.focus.FocusSessionStatus
import com.rewire.app.domain.focus.FocusState
import com.rewire.app.rewireViewModel
import com.rewire.app.ui.components.MorphingShape
import com.rewire.app.ui.components.heroBrush
import com.rewire.app.ui.components.SectionTitle
import com.rewire.app.ui.components.formatMinutes
import com.rewire.app.ui.theme.TimerTextStyle
import kotlinx.coroutines.launch

@Composable
fun FocusScreen() {
    val vm = rewireViewModel { FocusViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val bypass by vm.bypass.collectAsStateWithLifecycle()

    val mode = when (state.status) {
        FocusSessionStatus.IDLE -> 0
        FocusSessionStatus.COMPLETED, FocusSessionStatus.CANCELLED -> 2
        else -> 1
    }
    val motion = MaterialTheme.motionScheme
    val effects = motion.defaultEffectsSpec<Float>()
    val spatial = motion.defaultSpatialSpec<Float>()
    val fastEffects = motion.fastEffectsSpec<Float>()
    AnimatedContent(
        targetState = mode,
        transitionSpec = { (fadeIn(effects) + scaleIn(spatial, initialScale = 0.9f)).togetherWith(fadeOut(fastEffects)) },
        label = "focusMode",
    ) { m ->
        when (m) {
            0 -> FocusSetup(draft, bypass, vm::setDraft, vm::setBypass, vm::start)
            1 -> FocusRunning(state, now, vm::pause, vm::resume, vm::skipBreak, vm::end)
            else -> FocusFinished(state, onDone = vm::reset)
        }
    }
}

// ---- Setup --------------------------------------------------------------------------------------

private data class Preset(val focus: Int, val brk: Int)
private val presets = listOf(Preset(25, 5), Preset(50, 10), Preset(90, 20))

@Composable
private fun FocusSetup(
    draft: FocusConfig,
    bypass: FocusBypass?,
    onDraft: (FocusConfig) -> Unit,
    onBypass: (FocusBypass) -> Unit,
    onStart: () -> Unit,
) {
    val permission = rememberNotificationPermission()
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
    ) {
        Text("Focus", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 16.dp))
        Text("Deep work, then real rest.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
            MorphingShape(
                brush = Brush.radialGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface)),
                shapes = listOf(MaterialShapes.Cookie12Sided, MaterialShapes.Cookie9Sided),
                modifier = Modifier.size(240.dp),
                rotationMillis = 60_000,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("%d:00".format(draft.focusMinutes), style = TimerTextStyle, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    "${draft.cycles} × ${formatMinutes(draft.focusMinutes)}" + if (draft.breakMinutes > 0) " · ${formatMinutes(draft.breakMinutes)} breaks" else "",
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { p ->
                FilterChip(
                    selected = draft.focusMinutes == p.focus && draft.breakMinutes == p.brk,
                    onClick = { onDraft(draft.copy(focusMinutes = p.focus, breakMinutes = p.brk)) },
                    label = { Text("${p.focus} / ${p.brk}") },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.padding(vertical = 8.dp)) {
                Stepper("Focus", "${draft.focusMinutes} min", { onDraft(draft.copy(focusMinutes = (draft.focusMinutes - 5).coerceAtLeast(5))) }, { onDraft(draft.copy(focusMinutes = (draft.focusMinutes + 5).coerceAtMost(180))) }, draft.focusMinutes > 5, draft.focusMinutes < 180)
                Stepper("Break", if (draft.breakMinutes == 0) "None" else "${draft.breakMinutes} min", { onDraft(draft.copy(breakMinutes = (draft.breakMinutes - 1).coerceAtLeast(0))) }, { onDraft(draft.copy(breakMinutes = draft.breakMinutes + 1)) }, draft.breakMinutes > 0, draft.breakMinutes < draft.focusMinutes)
                Stepper("Cycles", "${draft.cycles}", { onDraft(draft.copy(cycles = (draft.cycles - 1).coerceAtLeast(1))) }, { onDraft(draft.copy(cycles = (draft.cycles + 1).coerceAtMost(12))) }, draft.cycles > 1, draft.cycles < 12)
            }
        }
        Text(
            when (draft.validate()) {
                FocusConfigError.BREAK_LONGER_THAN_FOCUS -> "Break can't be longer than focus."
                null -> "Break is always shorter than or equal to focus."
                else -> "Check your durations."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (draft.isValid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp),
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onStart,
            enabled = draft.isValid,
            modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
            contentPadding = ButtonDefaults.MediumContentPadding,
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Start focus", style = MaterialTheme.typography.titleMedium)
        }

        NotificationRationaleCard(
            permission,
            reason = "Rewire tells you when a break starts and when it's time to come back, even with the screen off.",
            modifier = Modifier.padding(top = 16.dp),
        )
        if (bypass != null) BypassCard(bypass, onBypass)
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit, canMinus: Boolean, canPlus: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
        FilledTonalIconButton(onClick = onMinus, enabled = canMinus, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Remove, contentDescription = "Decrease $label") }
        Spacer(Modifier.width(8.dp))
        FilledTonalIconButton(onClick = onPlus, enabled = canPlus, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Add, contentDescription = "Increase $label") }
    }
}

@Composable
private fun BypassCard(bypass: FocusBypass, onChange: (FocusBypass) -> Unit) {
    var confirmMax by remember { mutableStateOf(false) }
    SectionTitle("During focus")
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(vertical = 8.dp)) {
            BypassRow("Skip Minor reminders", "Quiet nudges while you work.", bypass.minor) { onChange(bypass.copy(minor = it)) }
            BypassRow("Skip Major pauses", "Guarded apps open without the full-screen pause.", bypass.major) { onChange(bypass.copy(major = it)) }
            BypassRow("Lift Max blocks", "Off by default. Blocks protect you most during focus.", bypass.max) { if (it) confirmMax = true else onChange(bypass.copy(max = false)) }
        }
    }
    if (confirmMax) {
        AlertDialog(
            onDismissRequest = { confirmMax = false },
            title = { Text("Lift Max blocks during focus?") },
            text = { Text("Apps you hard-blocked will open freely while a focus session runs. Most people keep this off.") },
            confirmButton = { TextButton(onClick = { onChange(bypass.copy(max = true)); confirmMax = false }) { Text("Lift blocks") } },
            dismissButton = { TextButton(onClick = { confirmMax = false }) { Text("Keep blocks") } },
        )
    }
}

@Composable
private fun BypassRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.semantics { contentDescription = title })
    }
}

// ---- Running ------------------------------------------------------------------------------------

@Composable
private fun FocusRunning(state: FocusState, now: Long, onPause: () -> Unit, onResume: () -> Unit, onSkipBreak: () -> Unit, onEnd: () -> Unit) {
    val paused = state.status == FocusSessionStatus.PAUSED
    val onBreak = state.phase == FocusSessionStatus.BREAK
    val c = MaterialTheme.colorScheme
    val ringColor = if (onBreak) c.tertiary else c.primary
    val amplitude by animateFloatAsState(if (paused) 0f else 1f, MaterialTheme.motionScheme.slowEffectsSpec(), label = "amp")
    val remaining = RewireNotifier.formatRemaining(state.remaining(now))

    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = CircleShape, color = if (onBreak) c.tertiaryContainer else c.primaryContainer, modifier = Modifier.padding(top = 16.dp)) {
            Text(
                when { paused -> "Paused"; onBreak -> "Break"; else -> "Deep work" },
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "$remaining remaining"
            stateDescription = if (paused) "Paused" else if (onBreak) "Break" else "Focusing"
        }) {
            CircularWavyProgressIndicator(
                progress = { state.progress(now) },
                color = ringColor,
                amplitude = { amplitude },
                modifier = Modifier.size(300.dp),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(remaining, style = TimerTextStyle)
                Text("Session ${state.cycle} / ${state.config.cycles}", style = MaterialTheme.typography.titleMedium, color = c.onSurfaceVariant)
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            if (onBreak) "Step away. Stretch, drink water, look far." else "Deep work mode. Guarded apps follow your focus rules.",
            style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        )
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            HoldToEnd(onEnd)
            FilledIconButton(
                onClick = if (paused) onResume else onPause,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(96.dp),
            ) {
                Icon(if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, contentDescription = if (paused) "Resume" else "Pause", modifier = Modifier.size(40.dp))
            }
            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                androidx.compose.animation.AnimatedVisibility(onBreak) {
                    FilledTonalIconButton(onClick = onSkipBreak, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(72.dp)) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Skip break")
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** Ending must be deliberate: press and hold ~1.5 s. Accessibility users get a normal click action. */
@Composable
private fun HoldToEnd(onEnd: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    Box(
        Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .semantics { contentDescription = "Hold to end session"; onClick("End session") { onEnd(); true } }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val job = scope.launch {
                        progress.animateTo(1f, tween(1500, easing = LinearEasing))
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onEnd()
                    }
                    tryAwaitRelease()
                    job.cancel()
                    scope.launch { progress.animateTo(0f, tween(200)) }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(progress.value).align(Alignment.BottomCenter).background(MaterialTheme.colorScheme.errorContainer))
        Icon(Icons.Rounded.Stop, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
    }
}

// ---- Finished -----------------------------------------------------------------------------------

@Composable
private fun FocusFinished(state: FocusState, onDone: () -> Unit) {
    val completed = state.status == FocusSessionStatus.COMPLETED
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MorphingShape(
            brush = heroBrush(),
            shapes = if (completed) listOf(MaterialShapes.SoftBurst, MaterialShapes.Flower, MaterialShapes.Sunny) else listOf(MaterialShapes.Circle, MaterialShapes.Pill),
            modifier = Modifier.size(180.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text(if (completed) "Session complete" else "Session ended early", style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            if (completed) "${state.config.cycles} blocks · ${formatMinutes(state.config.cycles * state.config.focusMinutes)} of deep work."
            else "Stopping is a choice too. It's recorded in Matrix.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        if (completed) Button(onClick = onDone) { Text("Done") } else OutlinedButton(onClick = onDone) { Text("Back to setup") }
    }
}
