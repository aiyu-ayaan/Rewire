package com.rewire.app.feature.guard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import com.rewire.app.service.accessibility.RewireAccessibilityService
import com.rewire.app.core.permissions.SystemPermissions
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Timer
import android.os.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewire.app.domain.analytics.DailyMetrics
import com.rewire.app.domain.habit.HabitProfile
import com.rewire.app.feature.landing.HERO_KEY
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rewire.app.ui.components.AppIcon
import com.rewire.app.ui.components.EmptyState
import com.rewire.app.ui.components.LevelBadge
import com.rewire.app.ui.components.MorphingShape
import com.rewire.app.ui.components.heroBrush
import com.rewire.app.ui.components.SectionTitle
import com.rewire.app.ui.components.formatMinutes
import com.rewire.app.ui.components.sharedBoundsOrSelf
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun GuardScreen(onOpenHabit: (String) -> Unit, onStartFocus: () -> Unit) {
    val vm = hiltViewModel<GuardViewModel>()
    val habits by vm.habits.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    val list = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { list.firstVisibleItemIndex == 0 } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 112.dp),
        ) {
            item { Header() }
            if (habits.any { it.habit.enabled }) item { ProtectionBanner() }
            item { TodayCard(today, habits.count { it.habit.enabled }, onStartFocus) }
            item { SectionTitle("Your habits", trailing = { if (habits.isNotEmpty()) Text("${habits.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }) }
            if (habits.isEmpty()) {
                item {
                    EmptyState(
                        title = "Nothing guarded yet",
                        body = "Group the apps that pull you in into a habit, then choose how much friction it gets.",
                        action = { FilledTonalButton(onClick = { creating = true }) { Text("Create first habit") } },
                    )
                }
            }
            items(habits, key = { it.id }) { profile ->
                HabitCard(
                    profile = profile,
                    usageMinutes = rememberLiveUsage(profile.apps) { vm.usageMinutesFor(profile) },
                    onClick = { onOpenHabit(profile.id) },
                    onToggle = { vm.setEnabled(profile, it) },
                    modifier = Modifier.animateItem().padding(bottom = 12.dp),
                )
            }
        }
        ExtendedFloatingActionButton(
            onClick = { creating = true },
            expanded = fabExpanded,
            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            text = { Text("New habit") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (creating) {
        CreateHabitSheet(
            onDismiss = { creating = false },
            onCreate = { name, level, apps ->
                val created = vm.create(name, level, apps)
                creating = false
                onOpenHabit(created.id)
            },
        )
    }
}

@Composable
private fun Header() {
    Row(Modifier.padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        // Same key as the landing hero: the big blob shrinks into this mark on first launch.
        MorphingShape(
            brush = heroBrush(),
            modifier = Modifier.size(44.dp).sharedBoundsOrSelf(HERO_KEY),
            rotationMillis = 40_000,
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Guard", style = MaterialTheme.typography.headlineLarge)
            Text(
                LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TodayCard(m: DailyMetrics, activeHabits: Int, onStartFocus: () -> Unit) {
    val score = m.disciplineScore
    val animated by animateFloatAsState(score ?: 0f, MaterialTheme.motionScheme.slowSpatialSpec(), label = "score")
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("TODAY", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.semantics {
                    contentDescription = score?.let { "Discipline ${(it * 100).toInt()} percent" } ?: "Discipline: no data yet"
                }) {
                    CircularWavyProgressIndicator(progress = { animated }, modifier = Modifier.size(112.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(score?.let { "${(it * 100).toInt()}%" } ?: "—", style = MaterialTheme.typography.headlineMedium)
                        Text("discipline", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("Focus", formatMinutes(m.focusMinutes))
                    if (m.screenTimeMinutes > 0) {
                        Stat("Screen time", formatMinutes(m.screenTimeMinutes))
                    }
                    Stat("Warnings", "${m.warningCount}")
                    Stat("Blocked", "${m.blockedAttempts}")
                    Stat("Guarding", if (activeHabits == 1) "1 habit" else "$activeHabits habits")
                }
            }
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onStartFocus, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Start focus")
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun HabitCard(
    profile: HabitProfile,
    usageMinutes: Int? = null,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth().sharedBoundsOrSelf("habit-${profile.id}"),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(profile.habit.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LevelBadge(profile.level)
                        if (usageMinutes != null && usageMinutes > 0) {
                            val limit = profile.rule.dailyLimitMinutes
                            Text(
                                if (limit != null) "${formatMinutes(usageMinutes)} / ${formatMinutes(limit)}" else "${formatMinutes(usageMinutes)} today",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (limit != null && usageMinutes >= limit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Switch(
                    checked = profile.habit.enabled,
                    onCheckedChange = onToggle,
                    thumbContent = if (profile.habit.enabled) { { Icon(Icons.Rounded.Check, null, Modifier.size(SwitchDefaults.IconSize)) } } else null,
                    modifier = Modifier.semantics { contentDescription = "Guard ${profile.habit.name}" },
                )
            }
            AnimatedVisibility(profile.apps.isNotEmpty()) {
                Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    profile.apps.take(5).forEachIndexed { i, app ->
                        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.offset(x = (-10 * i).dp)) {
                            AppIcon(app.packageName, size = 36.dp, modifier = Modifier.padding(2.dp))
                        }
                    }
                    val extra = profile.apps.size - 5
                    Text(
                        if (extra > 0) "+$extra more" else "${profile.apps.size} app${if (profile.apps.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.offset(x = (-10 * profile.apps.take(5).size + 18).dp).widthIn(min = 0.dp),
                    )
                }
            }
        }
    }
}

/**
 * Shown only when habits exist but neither the accessibility service nor the usage-access fallback can see apps.
 * Two cases: never enabled, or enabled but stopped by the system (toggle off/on fixes it).
 */
@Composable
private fun ProtectionBanner() {
    val context = LocalContext.current
    val running by RewireAccessibilityService.isRunning.collectAsStateWithLifecycle()
    var resumed by remember { mutableStateOf(0) }
    LifecycleResumeEffect(Unit) { resumed++; onPauseOrDispose { } }
    val enabled = remember(running, resumed) { SystemPermissions.accessibilityEnabled(context) }
    val fallback = remember(running, resumed) { SystemPermissions.usageFallbackReady(context) }
    val isAndroid13Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    var showRestrictedDialog by remember { mutableStateOf(false) }

    AnimatedVisibility(!running && !fallback) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.GppMaybe, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Protection is off", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (enabled) "Android stopped Rewire's service. Turn Rewire off and on in Accessibility."
                        else if (isAndroid13Plus) "Turn on Rewire in Accessibility. If disabled, tap Fix to allow restricted settings."
                        else "Turn on Rewire in Accessibility so Guard can see protected apps.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                FilledTonalButton(onClick = {
                    if (!enabled && isAndroid13Plus) {
                        showRestrictedDialog = true
                    } else {
                        SystemPermissions.open(context, SystemPermissions.accessibilitySettings())
                    }
                }) { Text("Fix") }
            }
        }
    }

    if (showRestrictedDialog) {
        AlertDialog(
            onDismissRequest = { showRestrictedDialog = false },
            icon = { Icon(Icons.Rounded.GppMaybe, contentDescription = null) },
            title = { Text("Enable Guard in Accessibility") },
            text = {
                Column {
                    Text("1. In Accessibility settings, find Rewire and turn it on.")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "2. If the setting says \"Restricted setting\": tap \"Open App Info\" below, tap the 3 dots (⋮) in the top-right corner, and choose \"Allow restricted settings\".",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showRestrictedDialog = false
                    SystemPermissions.open(context, SystemPermissions.accessibilitySettings())
                }) { Text("Accessibility") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showRestrictedDialog = false
                        SystemPermissions.open(context, SystemPermissions.appDetailsSettings(context))
                    }) { Text("Open App Info") }
                    TextButton(onClick = { showRestrictedDialog = false }) { Text("Cancel") }
                }
            },
        )
    }
}
