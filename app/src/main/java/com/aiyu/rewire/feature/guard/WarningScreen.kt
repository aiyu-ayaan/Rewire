package com.aiyu.rewire.feature.guard

import androidx.compose.foundation.layout.heightIn

import androidx.compose.foundation.layout.wrapContentWidth

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.rewire.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.aiyu.rewire.core.apps.InstalledAppsSource
import com.aiyu.rewire.core.settings.Settings
import com.aiyu.rewire.data.HabitRepository
import com.aiyu.rewire.data.WarningRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import com.aiyu.rewire.domain.habit.HabitProfile
import com.aiyu.rewire.domain.habit.WarningLevel
import com.aiyu.rewire.domain.warning.Warning
import com.aiyu.rewire.domain.warning.WarningPicker
import com.aiyu.rewire.ui.components.AppIcon
import com.aiyu.rewire.ui.components.LevelStyle
import com.aiyu.rewire.ui.components.MorphingShape
import com.aiyu.rewire.ui.components.formatClock
import com.aiyu.rewire.ui.components.style
import kotlinx.coroutines.delay

@HiltViewModel
class WarningPreviewViewModel @Inject constructor(
    val habits: HabitRepository,
    val warnings: WarningRepository,
    val settings: StateFlow<Settings?>,
    val installedApps: InstalledAppsSource,
) : ViewModel()

/** Preview of what the habit's warning will look like. Phase 3 reuses [WarningScreen] in the overlay. */
@Composable
fun WarningPreviewScreen(habitId: String, onClose: () -> Unit) {
    val vm = hiltViewModel<WarningPreviewViewModel>()
    val profile = remember(habitId) { vm.habits.habits.value.find { it.id == habitId } }
    if (profile == null) { LaunchedEffect(Unit) { onClose() }; return }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val warnings by vm.warnings.warnings.collectAsStateWithLifecycle()
    val warning = remember(profile.level) { WarningPicker.pick(warnings, profile.level) }
    val app = profile.apps.firstOrNull()?.packageName
    val appLabel = remember(app) { app?.let(vm.installedApps::label) ?: profile.habit.name }
    val userReason = settings?.profile?.reason?.takeIf { it.isNotBlank() }
    WarningScreen(profile, warning, app, appLabel, preview = true, userReason = userReason, onGoBack = onClose, onContinue = onClose)
}

@Composable
fun WarningScreen(
    profile: HabitProfile,
    warning: Warning?,
    packageName: String?,
    appLabel: String,
    preview: Boolean,
    userReason: String? = null,
    onGoBack: () -> Unit,
    onContinue: () -> Unit,
    /** Why a Max block fired (BlockReason name); null in previews. */
    blockReason: String? = null,
) {
    val level = profile.level
    val s = level.style()
    var unlockDialog by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Decorative slow morph in the corner — subtle warning tint.
        MorphingShape(
            brush = SolidColor(s.accent.copy(alpha = 0.08f)),
            shapes = when (level) {
                WarningLevel.MINOR -> listOf(MaterialShapes.Sunny, MaterialShapes.Cookie6Sided)
                WarningLevel.MAJOR -> listOf(MaterialShapes.Gem, MaterialShapes.SoftBurst)
                WarningLevel.MAX -> listOf(MaterialShapes.Cookie12Sided, MaterialShapes.Boom)
            },
            segmentMillis = 4000,
            rotationMillis = 40_000,
            modifier = Modifier.size(360.dp).align(Alignment.TopEnd).padding(start = 120.dp),
        )
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 600.dp).padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start,
        ) {
            if (preview) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Info, contentDescription = null, tint = s.accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.warning_preview_note),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (packageName != null) AppIcon(packageName, size = 48.dp)
                Spacer(Modifier.width(12.dp))
                Surface(
                    shape = CircleShape,
                    color = s.container,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(s.icon, contentDescription = s.label, tint = s.onContainer, modifier = Modifier.size(24.dp))
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                when (level) {
                    WarningLevel.MINOR -> stringResource(R.string.warning_minor_eyebrow)
                    WarningLevel.MAJOR -> stringResource(R.string.warning_major_eyebrow, appLabel)
                    WarningLevel.MAX -> stringResource(R.string.warning_max_title, appLabel)
                },
                style = MaterialTheme.typography.titleMedium,
                color = s.accent,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                warning?.title.orEmpty(),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))
            val reasonText = userReason ?: warning?.motivationalMessage
            if (!reasonText.isNullOrBlank()) {
                Spacer(Modifier.height(20.dp))
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.warning_reason),
                            style = MaterialTheme.typography.labelLarge,
                            color = s.accent,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.profile_reason_quote, reasonText),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            if (level == WarningLevel.MAX) {
                Spacer(Modifier.height(16.dp))
                val r = profile.rule
                val start = r.allowedStartMinutes
                val end = r.allowedEndMinutes
                when (blockReason) {
                    "LAUNCH_LIMIT" -> InfoRow(stringResource(R.string.warning_opens_today), stringResource(R.string.warning_opens_used, r.maxLaunches ?: 0, r.maxLaunches ?: 0))
                    "DAILY_LIMIT" -> InfoRow(stringResource(R.string.guard_daily_limit), stringResource(R.string.warning_limit_reached, r.dailyLimitMinutes ?: 0))
                    "ALWAYS" -> InfoRow(stringResource(R.string.warning_boundary), stringResource(R.string.warning_always_blocked))
                    "ESCALATION" -> InfoRow(stringResource(R.string.escalation_block_label), stringResource(R.string.escalation_block_value, r.escalationMaxAfterMinutes))
                }
                if (start != null && end != null) InfoRow(stringResource(R.string.warning_allowed_time), "${formatClock(start)} – ${formatClock(end)}")
                val nowMinutes = remember { java.time.LocalTime.now().let { it.hour * 60 + it.minute } }
                InfoRow(
                    stringResource(R.string.warning_next_available),
                    when {
                        blockReason == "ALWAYS" -> stringResource(R.string.warning_next_on_change)
                        blockReason == "OUTSIDE_WINDOW" && start != null && start > nowMinutes -> stringResource(R.string.warning_next_today, formatClock(start))
                        start != null -> stringResource(R.string.warning_next_tomorrow_at, formatClock(start))
                        else -> stringResource(R.string.warning_next_tomorrow)
                    },
                )
            }
            Spacer(Modifier.weight(1.2f))
            Actions(level, profile.rule.pauseSeconds, packageName, s, onGoBack, onContinue, onEmergency = { unlockDialog = true })
        }
    }

    if (unlockDialog) {
        AlertDialog(
            onDismissRequest = { unlockDialog = false },
            title = { Text(stringResource(R.string.warning_emergency_unlock)) },
            text = { Text(stringResource(R.string.warning_unlock_text) + if (preview) "\n\n" + stringResource(R.string.warning_unlock_preview) else "") },
            confirmButton = { TextButton(onClick = { unlockDialog = false; if (!preview) onContinue() }) { Text(stringResource(R.string.warning_unlock)) } },
            dismissButton = { TextButton(onClick = { unlockDialog = false }) { Text(stringResource(R.string.warning_stay_blocked)) } },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun Actions(
    level: WarningLevel,
    pauseSeconds: Int,
    packageName: String?,
    s: LevelStyle,
    onGoBack: () -> Unit,
    onContinue: () -> Unit,
    onEmergency: () -> Unit,
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().widthIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        when (level) {
            WarningLevel.MINOR -> Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight)
            ) {
                Text(stringResource(R.string.warning_continue), style = MaterialTheme.typography.titleMedium)
            }
            WarningLevel.MAJOR -> {
                var left by remember { mutableIntStateOf(pauseSeconds) }
                LaunchedEffect(Unit) { while (left > 0) { delay(1000); left-- } }
                val progress by animateFloatAsState(if (pauseSeconds == 0) 1f else 1f - left / pauseSeconds.toFloat(), label = "pause")
                Button(
                    onClick = onGoBack,
                    colors = ButtonDefaults.buttonColors(containerColor = s.accent, contentColor = MaterialTheme.colorScheme.onTertiary),
                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight)
                ) {
                    Text(stringResource(R.string.warning_go_back), style = MaterialTheme.typography.titleMedium)
                }
                // Always laid out (alpha only) so buttons don't jump when the pause ends.
                LinearWavyProgressIndicator(
                    progress = { progress },
                    color = s.accent,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).alpha(if (left > 0) 1f else 0f)
                )
                TextButton(
                    onClick = onContinue,
                    enabled = left == 0,
                    colors = ButtonDefaults.textButtonColors(contentColor = s.accent)
                ) {
                    Text(if (left > 0) stringResource(R.string.warning_continue_in, left) else stringResource(R.string.warning_continue_anyway))
                }
            }
            WarningLevel.MAX -> {
                Button(
                    onClick = onGoBack,
                    colors = ButtonDefaults.buttonColors(containerColor = s.accent, contentColor = MaterialTheme.colorScheme.onError),
                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight)
                ) {
                    Text(stringResource(R.string.warning_back), style = MaterialTheme.typography.titleMedium)
                }
                if (packageName != null) {
                    TextButton(
                        onClick = {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            runCatching { context.startActivity(intent) }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    ) {
                        Icon(Icons.Rounded.DoNotDisturbOn, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.warning_mute_notifications))
                    }
                }
                TextButton(
                    onClick = onEmergency,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.outline)
                ) {
                    Text(stringResource(R.string.warning_emergency_unlock), textAlign = TextAlign.Center)
                }
            }
        }
    }
}
