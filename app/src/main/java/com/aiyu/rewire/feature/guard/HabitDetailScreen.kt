package com.aiyu.rewire.feature.guard

import androidx.compose.ui.semantics.stateDescription
import com.aiyu.rewire.domain.habit.AppLimits
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.ExperimentalLayoutApi

import androidx.compose.foundation.layout.FlowRow

import androidx.compose.foundation.verticalScroll

import androidx.compose.foundation.rememberScrollState

import com.aiyu.rewire.ui.components.readableWidth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.aiyu.rewire.R
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Visibility
import com.aiyu.rewire.core.permissions.SystemPermissions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.aiyu.rewire.domain.habit.HabitProfile
import com.aiyu.rewire.domain.habit.WarningLevel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.aiyu.rewire.ui.components.AppIcon
import com.aiyu.rewire.ui.components.SectionTitle
import com.aiyu.rewire.ui.components.formatClock
import com.aiyu.rewire.ui.components.formatMinutes
import com.aiyu.rewire.ui.components.sharedBoundsOrSelf
import com.aiyu.rewire.ui.components.style
import kotlin.math.roundToInt

@Composable
fun HabitDetailScreen(habitId: String, onBack: () -> Unit, onPreview: (String) -> Unit) {
    val vm = hiltViewModel<HabitDetailViewModel, HabitDetailViewModel.Factory>(key = habitId) { it.create(habitId) }
    val profile by vm.habit.collectAsStateWithLifecycle()
    val p = profile ?: return // deleted -> caller already popped
    var pickingApps by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var limitsFor by rememberSaveable { mutableStateOf<String?>(null) }
    val levelStyle = p.level.style()
    val container by animateColorAsState(levelStyle.container, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "c")
    val enabledDesc = stringResource(R.string.guard_habit_enabled_desc)

    Surface(Modifier.fillMaxSize().sharedBoundsOrSelf("habit-${p.id}"), color = MaterialTheme.colorScheme.surface) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {},
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.warning_back)) } },
                    actions = {
                        Switch(
                            checked = p.habit.enabled,
                            onCheckedChange = vm::setEnabled,
                            modifier = Modifier.padding(end = 12.dp).semantics { contentDescription = enabledDesc },
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = container),
                )
            },
        ) { padding ->
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), modifier = Modifier.padding(padding)) {
                item {
                    Surface(color = container, contentColor = levelStyle.onContainer, shape = MaterialTheme.shapes.extraLarge.copy(topStart = androidx.compose.foundation.shape.CornerSize(0), topEnd = androidx.compose.foundation.shape.CornerSize(0))) {
                        Column(Modifier.readableWidth().padding(start = 24.dp, end = 24.dp, bottom = 28.dp)) {
                            Icon(levelStyle.icon, contentDescription = null, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(p.habit.name, style = MaterialTheme.typography.displaySmall)
                            Text(
                                stringResource(R.string.guard_detail_summary, levelStyle.label, pluralStringResource(R.plurals.guard_app_count, p.apps.size, p.apps.size)) + if (!p.habit.enabled) stringResource(R.string.guard_detail_paused) else "",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
                item {
                    Column(Modifier.readableWidth().padding(horizontal = 16.dp)) {
                        SectionTitle(stringResource(R.string.guard_friction_level))
                        LevelSelector(p.level, vm::setLevel)
                        Text(levelStyle.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                        FilledTonalButton(onClick = { onPreview(p.id) }, modifier = Modifier.padding(top = 12.dp)) {
                            Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.guard_preview_screen, levelStyle.label))
                        }
                        SectionTitle(stringResource(R.string.guard_boundaries))
                        BoundariesCard(p, vm)
                        SectionTitle(stringResource(R.string.matrix_protected_apps), trailing = {
                            TextButton(onClick = { pickingApps = true }) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.guard_edit))
                            }
                        })
                        if (p.apps.isEmpty()) {
                            Text(stringResource(R.string.guard_no_apps), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(p.apps, key = { it.packageName }) { app ->
                    val ctx = LocalContext.current
                    val label = remember(app.packageName) { vm.appLabel(app.packageName) }
                    val used = rememberLiveUsage(app.packageName) { vm.appUsageMinutesToday(app.packageName) }
                    val own = app.limits.any
                    ListItem(
                        headlineContent = { Text(label) },
                        supportingContent = {
                            val usedText = used?.let { stringResource(R.string.guard_usage_today, formatMinutes(it)) }
                            val text = listOfNotNull(usedText, stringResource(if (own) R.string.guard_app_own_limits else R.string.guard_app_habit_limits)).joinToString(" · ")
                            Text(text)
                        },
                        leadingContent = { AppIcon(app.packageName) },
                        trailingContent = { IconButton(onClick = { vm.removeApp(app.packageName) }) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.guard_remove_app, label)) } },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.animateItem().readableWidth().padding(horizontal = 4.dp)
                            .clickable(onClickLabel = stringResource(R.string.guard_app_limits_open, label), role = Role.Button) { limitsFor = app.packageName },
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.readableWidth().padding(horizontal = 16.dp, vertical = 32.dp),
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.guard_delete_habit))
                    }
                }
            }
        }
    }

    limitsFor?.let { pkg ->
        val app = p.apps.firstOrNull { it.packageName == pkg }
        if (app == null) limitsFor = null
        else ModalBottomSheet(onDismissRequest = { limitsFor = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            AppLimitsSheet(p, app.packageName, app.limits, vm.appLabel(pkg)) { vm.setAppLimits(pkg, it) }
        }
    }

    if (pickingApps) {
        val selected = remember { mutableStateListOf<String>().apply { addAll(p.apps.map { it.packageName }) } }
        ModalBottomSheet(onDismissRequest = { vm.setApps(selected.toList()); pickingApps = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
                Text(stringResource(R.string.matrix_protected_apps), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 12.dp))
                AppPicker(selected, Modifier.weight(1f, fill = false))
                Button(onClick = { vm.setApps(selected.toList()); pickingApps = false }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text(stringResource(R.string.guard_save_count, selected.size)) }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
            title = { Text(stringResource(R.string.guard_delete_title, p.habit.name)) },
            text = { Text(stringResource(R.string.guard_delete_text)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onBack(); vm.delete() }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

/**
 * Today's usage read straight from UsageStatsManager (never persisted): re-read on every resume and
 * each minute while visible, off the main thread.
 */
@Composable
internal fun rememberLiveUsage(key: Any?, read: () -> Int?): Int? {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState<Int?>(null, key, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                value = withContext(Dispatchers.IO) { read() }
                delay(60_000L)
            }
        }
    }.value
}

@Composable
private fun BoundariesCard(p: HabitProfile, vm: HabitDetailViewModel) {
    val usage = rememberLiveUsage(p.apps) { vm.usageMinutesToday() }
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (usage != null && usage > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(if (p.apps.size > 1) R.string.guard_usage_combined else R.string.guard_usage_single), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(formatMinutes(usage), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            // 0 on slider = no limit
            SliderSetting(
                title = stringResource(R.string.guard_daily_limit),
                value = p.rule.dailyLimitMinutes ?: 0, range = 0..240, step = 15,
                display = { if (it == 0) stringResource(R.string.guard_no_limit) else formatMinutes(it) },
                onCommit = { vm.setDailyLimit(it.takeIf { v -> v > 0 }) },
            )
            if (p.rule.dailyLimitMinutes != null && !vm.hasUsageAccess) {
                val context = LocalContext.current
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.guard_usage_access_required),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Text(
                                stringResource(R.string.guard_usage_access_text),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            FilledTonalButton(
                                onClick = {
                                    SystemPermissions.open(
                                        context,
                                        SystemPermissions.usageAccessSettings(context),
                                    )
                                },
                                modifier = Modifier.padding(top = 8.dp),
                            ) {
                                Text(stringResource(R.string.guard_grant))
                            }
                        }
                    }
                }
            }
            SliderSetting(
                title = stringResource(R.string.guard_launch_limit),
                value = p.rule.maxLaunches ?: 0, range = 0..30, step = 1,
                display = { if (it == 0) stringResource(R.string.guard_no_limit) else stringResource(R.string.guard_opens_per_day, it) },
                onCommit = { vm.setMaxLaunches(it.takeIf { v -> v > 0 }) },
            )
            AnimatedVisibility(p.level == WarningLevel.MAJOR) {
                SliderSetting(
                    title = stringResource(R.string.guard_pause_before_continue),
                    value = p.rule.pauseSeconds, range = 0..30, step = 1,
                    display = { if (it == 0) stringResource(R.string.guard_instant) else stringResource(R.string.guard_seconds, it) },
                    onCommit = vm::setPause,
                )
            }
            AllowedWindow(p.rule.allowedStartMinutes, p.rule.allowedEndMinutes, vm::setWindow)
            EscalationSection(p, vm)
        }
    }
}

@Composable
private fun EscalationSection(p: HabitProfile, vm: HabitDetailViewModel) {
    val r = p.rule
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.escalation_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.escalation_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val escalationDesc = stringResource(R.string.escalation_title)
            Switch(checked = r.escalationEnabled, onCheckedChange = vm::setEscalation, modifier = Modifier.semantics { contentDescription = escalationDesc })
        }
        AnimatedVisibility(r.escalationEnabled) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SliderSetting(
                    title = stringResource(R.string.escalation_major_after),
                    value = r.escalationMajorAfterMinutes, range = 5..235, step = 5,
                    display = { formatMinutes(it) }, onCommit = vm::setEscalationMajor,
                )
                SliderSetting(
                    title = stringResource(R.string.escalation_max_after),
                    value = r.escalationMaxAfterMinutes, range = 10..240, step = 5,
                    display = { formatMinutes(it) }, onCommit = vm::setEscalationMax,
                )
                Text(stringResource(R.string.escalation_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SliderSetting(title: String, value: Int, range: IntRange, step: Int, display: @Composable (Int) -> String, onCommit: (Int) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val shown = display(local.roundToInt())
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(shown, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = local,
            // Continuous track (no dot per stop — too dense for 30+ stops), snapped to [step].
            onValueChange = { local = ((it / step).roundToInt() * step).toFloat() },
            onValueChangeFinished = { onCommit(local.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            modifier = Modifier.semantics { contentDescription = title; stateDescription = shown },
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AllowedWindow(start: Int?, end: Int?, onChange: (Int?, Int?) -> Unit) {
    var editing by remember { mutableStateOf<Boolean?>(null) } // true = start, false = end
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.guard_allowed_window), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (start != null) TextButton(onClick = { onChange(null, null) }) { Text(stringResource(R.string.guard_clear)) }
        }
        Text(
            stringResource(if (start == null) R.string.guard_window_any else R.string.guard_window_set),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            AssistChip(onClick = { editing = true }, label = { Text(stringResource(R.string.guard_window_from, start?.let(::formatClock) ?: "--:--")) })
            AssistChip(onClick = { editing = false }, label = { Text(stringResource(R.string.guard_window_to, end?.let(::formatClock) ?: "--:--")) })
        }
    }
    val which = editing ?: return
    val initial = (if (which) start else end) ?: if (which) 9 * 60 else 9 * 60 + 30
    val state = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60)
    AlertDialog(
        onDismissRequest = { editing = null },
        title = { Text(stringResource(if (which) R.string.guard_allowed_from else R.string.guard_allowed_until)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { TimePicker(state) } },
        confirmButton = {
            TextButton(onClick = {
                val picked = state.hour * 60 + state.minute
                // Choosing one side fills the other with a sensible 30 min window.
                if (which) onChange(picked, end ?: ((picked + 30) % (24 * 60))) else onChange(start ?: ((picked - 30 + 24 * 60) % (24 * 60)), picked)
                editing = null
            }) { Text(stringResource(R.string.guard_set)) }
        },
        dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/**
 * One app's limits. Each boundary follows the habit (global) until the user switches it to this app's own value;
 * switching one on starts from the habit's value, so nothing changes until it is edited.
 */
@Composable
private fun AppLimitsSheet(p: HabitProfile, pkg: String, limits: AppLimits, label: String, onChange: (AppLimits) -> Unit) {
    val r = p.rule
    val noLimit = stringResource(R.string.guard_no_limit)
    val habitDaily = r.dailyLimitMinutes?.let { formatMinutes(it) } ?: noLimit
    val habitLaunches = r.maxLaunches?.let { stringResource(R.string.guard_opens_per_day, it) } ?: noLimit
    val habitWindow = if (r.allowedStartMinutes != null && r.allowedEndMinutes != null) "${formatClock(r.allowedStartMinutes)} – ${formatClock(r.allowedEndMinutes)}" else stringResource(R.string.guard_app_window_any)
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(pkg)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.guard_app_limits_title, label), style = MaterialTheme.typography.headlineSmall)
        }
        Text(stringResource(R.string.guard_app_limits_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        OverrideRow(stringResource(R.string.guard_app_own_daily), stringResource(R.string.guard_app_habit_value, habitDaily), limits.ownDailyLimit) { on ->
            onChange(limits.copy(ownDailyLimit = on, dailyLimitMinutes = if (on) r.dailyLimitMinutes else null))
        }
        AnimatedVisibility(limits.ownDailyLimit) {
            SliderSetting(
                title = stringResource(R.string.guard_daily_limit),
                value = limits.dailyLimitMinutes ?: 0, range = 0..240, step = 15,
                display = { if (it == 0) stringResource(R.string.guard_no_limit) else formatMinutes(it) },
                onCommit = { onChange(limits.copy(dailyLimitMinutes = it.takeIf { v -> v > 0 })) },
            )
        }

        OverrideRow(stringResource(R.string.guard_app_own_launch), stringResource(R.string.guard_app_habit_value, habitLaunches), limits.ownLaunchLimit) { on ->
            onChange(limits.copy(ownLaunchLimit = on, maxLaunches = if (on) r.maxLaunches else null))
        }
        AnimatedVisibility(limits.ownLaunchLimit) {
            SliderSetting(
                title = stringResource(R.string.guard_launch_limit),
                value = limits.maxLaunches ?: 0, range = 0..30, step = 1,
                display = { if (it == 0) stringResource(R.string.guard_no_limit) else stringResource(R.string.guard_opens_per_day, it) },
                onCommit = { onChange(limits.copy(maxLaunches = it.takeIf { v -> v > 0 })) },
            )
        }

        OverrideRow(stringResource(R.string.guard_app_own_window), stringResource(R.string.guard_app_habit_value, habitWindow), limits.ownWindow) { on ->
            onChange(limits.copy(ownWindow = on, allowedStartMinutes = if (on) r.allowedStartMinutes else null, allowedEndMinutes = if (on) r.allowedEndMinutes else null))
        }
        AnimatedVisibility(limits.ownWindow) {
            AllowedWindow(limits.allowedStartMinutes, limits.allowedEndMinutes) { s, e -> onChange(limits.copy(allowedStartMinutes = s, allowedEndMinutes = e)) }
        }
    }
}

@Composable
private fun OverrideRow(title: String, habitValue: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(habitValue, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
