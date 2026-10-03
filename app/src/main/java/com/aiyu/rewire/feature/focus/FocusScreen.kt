package com.aiyu.rewire.feature.focus

import androidx.compose.ui.semantics.Role

import androidx.compose.ui.semantics.role

import androidx.compose.ui.semantics.LiveRegionMode

import androidx.compose.ui.semantics.liveRegion

import com.aiyu.rewire.ui.components.CappedFontScale

import androidx.compose.foundation.layout.heightIn

import com.aiyu.rewire.ui.components.readableWidth

import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.res.stringResource
import com.aiyu.rewire.R
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
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.History
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.rewire.BuildConfig
import com.aiyu.rewire.core.settings.FocusBypass
import com.aiyu.rewire.core.notifications.NotificationRationaleCard
import com.aiyu.rewire.core.notifications.RewireNotifier
import com.aiyu.rewire.core.notifications.rememberNotificationPermission
import com.aiyu.rewire.domain.focus.FocusConfig
import com.aiyu.rewire.domain.focus.FocusConfigError
import com.aiyu.rewire.domain.focus.FocusPreset
import com.aiyu.rewire.domain.focus.FocusPresetError
import com.aiyu.rewire.domain.focus.FocusSessionStatus
import com.aiyu.rewire.domain.focus.FocusState
import com.aiyu.rewire.ui.components.MorphingShape
import com.aiyu.rewire.ui.components.heroBrush
import com.aiyu.rewire.ui.components.SectionTitle
import com.aiyu.rewire.ui.HideNavigationBar
import com.aiyu.rewire.ui.components.formatMinutes
import com.aiyu.rewire.ui.components.sharedBoundsOrSelf
import com.aiyu.rewire.ui.theme.TimerTextStyle
import kotlinx.coroutines.launch

@Composable
fun FocusScreen(onFullscreen: () -> Unit, onHistory: () -> Unit) {
    val vm = focusViewModel()
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val bypass by vm.bypass.collectAsStateWithLifecycle()
    val userPresets by vm.userPresets.collectAsStateWithLifecycle()
    val focusDnd by vm.focusDndEnabled.collectAsStateWithLifecycle()
    val awaitingNote by vm.awaitingNote.collectAsStateWithLifecycle()

    var resumeCount by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { resumeCount++; onPauseOrDispose { } }
    val isDndGranted = remember(resumeCount) { vm.isDndAccessGranted }

    val mode = when (state.status) {
        FocusSessionStatus.IDLE -> 0
        FocusSessionStatus.COMPLETED, FocusSessionStatus.CANCELLED -> 2
        else -> 1
    }
    // Setup is the tab's base screen; timer + result are "inside" it, so the bottom bar steps away.
    HideNavigationBar(hide = mode != 0)
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
            0 -> FocusSetup(
                draft = draft,
                bypass = bypass,
                focusDnd = focusDnd ?: true,
                isDndGranted = isDndGranted,
                userPresets = userPresets,
                onSavePreset = vm::savePreset,
                onRenamePreset = vm::renamePreset,
                onDeletePreset = { vm.deletePreset(it) },
                onDraft = vm::setDraft,
                onBypass = vm::setBypass,
                onFocusDnd = vm::setFocusDndEnabled,
                onOpenDndSettings = { context.startActivity(vm.dndSettingsIntent()) },
                onStart = vm::start,
                onQuickTest = vm::startQuickTest,
                onHistory = onHistory,
            )
            1 -> FocusRunning(state, now, vm::pause, vm::resume, vm::skipBreak, vm::end, onFullscreen)
            else -> FocusFinished(state, onDone = vm::reset, onHistory = onHistory)
        }
    }
    // Optional: Skip (or dismiss) leaves the session in history without a note.
    awaitingNote?.let { s ->
        AchievementDialog(
            initial = "",
            completed = s.state.status == FocusSessionStatus.COMPLETED,
            onSave = vm::saveNote,
            onDismiss = vm::skipNote,
        )
    }
}

// ---- Setup --------------------------------------------------------------------------------------

@Composable
private fun FocusSetup(
    draft: FocusConfig,
    bypass: FocusBypass?,
    focusDnd: Boolean,
    isDndGranted: Boolean,
    userPresets: List<FocusPreset>,
    onSavePreset: suspend (String) -> FocusPresetError?,
    onRenamePreset: suspend (String, String) -> FocusPresetError?,
    onDeletePreset: (String) -> Unit,
    onDraft: (FocusConfig) -> Unit,
    onBypass: (FocusBypass) -> Unit,
    onFocusDnd: (Boolean) -> Unit,
    onOpenDndSettings: () -> Unit,
    onStart: () -> Unit,
    onQuickTest: () -> Unit,
    onHistory: () -> Unit,
) {
    val permission = rememberNotificationPermission()
    Column(
        Modifier.fillMaxSize().statusBarsPadding().readableWidth(720.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.nav_focus), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            FilledTonalIconButton(onClick = onHistory, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.Rounded.History, contentDescription = stringResource(R.string.focus_history))
            }
        }
        Text(stringResource(R.string.focus_tagline), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
            MorphingShape(
                brush = Brush.radialGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface)),
                shapes = listOf(MaterialShapes.Cookie12Sided, MaterialShapes.Cookie9Sided),
                modifier = Modifier.size(240.dp),
                rotationMillis = 60_000,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CappedFontScale { Text("%d:00".format(draft.focusMinutes), style = TimerTextStyle, color = MaterialTheme.colorScheme.onPrimaryContainer) }
                Text(
                    stringResource(R.string.focus_summary, draft.cycles, formatMinutes(draft.focusMinutes)) + if (draft.breakMinutes > 0) stringResource(R.string.focus_summary_breaks, formatMinutes(draft.breakMinutes)) else "",
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        FocusPresetRow(
            draft = draft,
            userPresets = userPresets,
            onApply = onDraft,
            onSave = onSavePreset,
            onRename = onRenamePreset,
            onDelete = onDeletePreset,
        )
        Spacer(Modifier.height(12.dp))
        Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.padding(vertical = 8.dp)) {
                Stepper(stringResource(R.string.matrix_stat_focus), stringResource(R.string.focus_minutes_value, draft.focusMinutes), { onDraft(draft.copy(focusMinutes = (draft.focusMinutes - 5).coerceAtLeast(5))) }, { onDraft(draft.copy(focusMinutes = (draft.focusMinutes + 5).coerceAtMost(180))) }, draft.focusMinutes > 5, draft.focusMinutes < 180)
                Stepper(stringResource(R.string.matrix_stat_break), if (draft.breakMinutes == 0) stringResource(R.string.focus_none) else stringResource(R.string.focus_minutes_value, draft.breakMinutes), { onDraft(draft.copy(breakMinutes = (draft.breakMinutes - 1).coerceAtLeast(0))) }, { onDraft(draft.copy(breakMinutes = draft.breakMinutes + 1)) }, draft.breakMinutes > 0, draft.breakMinutes < draft.focusMinutes)
                Stepper(stringResource(R.string.focus_cycles), "${draft.cycles}", { onDraft(draft.copy(cycles = (draft.cycles - 1).coerceAtLeast(1))) }, { onDraft(draft.copy(cycles = (draft.cycles + 1).coerceAtMost(12))) }, draft.cycles > 1, draft.cycles < 12)
            }
        }
        Text(
            when (draft.validate()) {
                FocusConfigError.BREAK_LONGER_THAN_FOCUS -> stringResource(R.string.focus_error_break_longer)
                null -> stringResource(R.string.focus_hint_break)
                else -> stringResource(R.string.focus_error_generic)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (draft.isValid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp),
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onStart,
            enabled = draft.isValid,
            modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
            contentPadding = ButtonDefaults.MediumContentPadding,
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.guard_start_focus), style = MaterialTheme.typography.titleMedium)
        }
        if (BuildConfig.DEBUG) {
            OutlinedButton(onClick = onQuickTest, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Quick test · 20s focus / 10s break")
            }
        }

        NotificationRationaleCard(
            permission,
            reason = stringResource(R.string.focus_notification_reason),
            modifier = Modifier.padding(top = 16.dp),
        )
        if (bypass != null) {
            BypassCard(
                bypass = bypass,
                onChange = onBypass,
                focusDnd = focusDnd,
                onFocusDnd = onFocusDnd,
                isDndGranted = isDndGranted,
                onOpenDndSettings = onOpenDndSettings,
            )
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit, canMinus: Boolean, canPlus: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
        FilledTonalIconButton(onClick = onMinus, enabled = canMinus, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.focus_decrease, label)) }
        Spacer(Modifier.width(8.dp))
        FilledTonalIconButton(onClick = onPlus, enabled = canPlus, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.focus_increase, label)) }
    }
}

@Composable
private fun BypassCard(
    bypass: FocusBypass,
    onChange: (FocusBypass) -> Unit,
    focusDnd: Boolean,
    onFocusDnd: (Boolean) -> Unit,
    isDndGranted: Boolean,
    onOpenDndSettings: () -> Unit,
) {
    var confirmMax by remember { mutableStateOf(false) }
    var showDndDialog by remember { mutableStateOf(false) }
    SectionTitle(stringResource(R.string.focus_during))
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(vertical = 8.dp)) {
            BypassRow(
                stringResource(R.string.focus_dnd_title),
                stringResource(if (!isDndGranted) R.string.focus_dnd_grant else R.string.focus_dnd_body),
                checked = focusDnd && isDndGranted,
            ) { checked ->
                if (!isDndGranted) {
                    showDndDialog = true
                } else {
                    onFocusDnd(checked)
                }
            }
            BypassRow(stringResource(R.string.focus_skip_minor), stringResource(R.string.focus_skip_minor_body), bypass.minor) { onChange(bypass.copy(minor = it)) }
            BypassRow(stringResource(R.string.focus_skip_major), stringResource(R.string.focus_skip_major_body), bypass.major) { onChange(bypass.copy(major = it)) }
            BypassRow(stringResource(R.string.focus_lift_max), stringResource(R.string.focus_lift_max_body), bypass.max) { if (it) confirmMax = true else onChange(bypass.copy(max = false)) }
        }
    }
    if (confirmMax) {
        AlertDialog(
            onDismissRequest = { confirmMax = false },
            title = { Text(stringResource(R.string.focus_lift_max_title)) },
            text = { Text(stringResource(R.string.focus_lift_max_text)) },
            confirmButton = { TextButton(onClick = { onChange(bypass.copy(max = true)); confirmMax = false }) { Text(stringResource(R.string.focus_lift_blocks)) } },
            dismissButton = { TextButton(onClick = { confirmMax = false }) { Text(stringResource(R.string.focus_keep_blocks)) } },
        )
    }
    if (showDndDialog) {
        AlertDialog(
            onDismissRequest = { showDndDialog = false },
            title = { Text(stringResource(R.string.focus_dnd_dialog_title)) },
            text = { Text(stringResource(R.string.focus_dnd_dialog_text)) },
            confirmButton = { TextButton(onClick = { showDndDialog = false; onOpenDndSettings() }) { Text(stringResource(R.string.focus_open_settings)) } },
            dismissButton = { TextButton(onClick = { showDndDialog = false }) { Text(stringResource(R.string.focus_not_now)) } },
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
private fun FocusRunning(state: FocusState, now: Long, onPause: () -> Unit, onResume: () -> Unit, onSkipBreak: () -> Unit, onEnd: () -> Unit, onFullscreen: () -> Unit) {
    val paused = state.status == FocusSessionStatus.PAUSED
    val onBreak = state.phase == FocusSessionStatus.BREAK
    val c = MaterialTheme.colorScheme
    val ringColor = if (onBreak) c.tertiary else c.primary
    val amplitude by animateFloatAsState(if (paused) 0f else 1f, MaterialTheme.motionScheme.slowEffectsSpec(), label = "amp")
    val stateDesc = stringResource(if (paused) R.string.focus_paused else if (onBreak) R.string.matrix_stat_break else R.string.focus_state_focusing)

    val header: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
            Surface(shape = CircleShape, color = if (onBreak) c.tertiaryContainer else c.primaryContainer) {
                Text(
                    stringResource(when { paused -> R.string.focus_paused; onBreak -> R.string.matrix_stat_break; else -> R.string.notif_focus_title }),
                    style = MaterialTheme.typography.labelLarge,
                    // Phase changes (focus / break / paused) are announced once; the countdown itself is not live.
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            FilledTonalIconButton(onClick = onFullscreen, shapes = IconButtonDefaults.shapes(), modifier = Modifier.align(Alignment.CenterEnd)) {
                Icon(Icons.Rounded.Fullscreen, contentDescription = stringResource(R.string.focus_fullscreen))
            }
        }
    }
    val ring: @Composable (Dp) -> Unit = { size ->
        Box(contentAlignment = Alignment.Center, modifier = Modifier.semantics(mergeDescendants = true) {
            stateDescription = stateDesc
        }) {
            CircularWavyProgressIndicator(
                progress = { state.progress(now) },
                color = ringColor,
                amplitude = { amplitude },
                modifier = Modifier.size(size),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CappedFontScale { RollingTime(state.remaining(now), TimerTextStyle, c.onSurface, Modifier.sharedBoundsOrSelf(TIMER_KEY)) }
                Text(stringResource(R.string.focus_session_of, state.cycle, state.config.cycles), style = MaterialTheme.typography.titleMedium, color = c.onSurfaceVariant)
            }
        }
    }
    val caption: @Composable () -> Unit = {
        Text(
            stringResource(if (onBreak) R.string.focus_caption_break else R.string.focus_caption_focus),
            style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        )
    }
    val controls: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            HoldToEnd(onEnd)
            FilledIconButton(
                onClick = if (paused) onResume else onPause,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(96.dp),
            ) {
                Icon(if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, contentDescription = stringResource(if (paused) R.string.focus_resume else R.string.focus_pause), modifier = Modifier.size(40.dp))
            }
            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                androidx.compose.animation.AnimatedVisibility(onBreak) {
                    FilledTonalIconButton(onClick = onSkipBreak, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(72.dp)) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.focus_skip_break))
                    }
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().displayCutoutPadding().padding(16.dp)) {
        val areaHeight = maxHeight
        val ringInLandscape = min(300.dp, maxHeight - 16.dp)
        if (maxWidth > maxHeight) {
            // Landscape: two panes. The ring takes the short side (never squashed); header, caption and controls
            // stack beside it, all within the height.
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { ring(ringInLandscape) }
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).heightIn(min = areaHeight),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {
                    header()
                    caption()
                    controls()
                }
            }
        } else {
            // Scrolls when text is large; spreads out evenly when there is room.
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = areaHeight),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                header()
                Spacer(Modifier.height(16.dp))
                ring(300.dp)
                Spacer(Modifier.height(16.dp))
                caption()
                Spacer(Modifier.height(24.dp))
                controls()
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** Ending must be deliberate: press and hold ~1.5 s. Accessibility users get a normal click action. */
@Composable
private fun HoldToEnd(onEnd: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val holdDesc = stringResource(R.string.focus_hold_to_end)
    val endLabel = stringResource(R.string.focus_end_session)
    Box(
        Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .semantics { role = Role.Button; contentDescription = holdDesc; onClick(endLabel) { onEnd(); true } }
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
private fun FocusFinished(state: FocusState, onDone: () -> Unit, onHistory: () -> Unit) {
    val completed = state.status == FocusSessionStatus.COMPLETED
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MorphingShape(
            brush = heroBrush(),
            shapes = if (completed) listOf(MaterialShapes.SoftBurst, MaterialShapes.Flower, MaterialShapes.Sunny) else listOf(MaterialShapes.Circle, MaterialShapes.Pill),
            modifier = Modifier.size(180.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text(stringResource(if (completed) R.string.notif_completed_title else R.string.focus_ended_early), style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            if (completed) stringResource(R.string.focus_finished_text, state.config.cycles, formatMinutes(state.config.cycles * state.config.focusMinutes))
            else stringResource(R.string.focus_ended_text),
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        if (completed) Button(onClick = onDone) { Text(stringResource(R.string.focus_done)) } else OutlinedButton(onClick = onDone) { Text(stringResource(R.string.focus_back_to_setup)) }
        TextButton(onClick = onHistory, modifier = Modifier.padding(top = 8.dp)) {
            Icon(Icons.Rounded.History, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.focus_history))
        }
    }
}
