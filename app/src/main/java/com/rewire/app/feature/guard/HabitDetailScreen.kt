package com.rewire.app.feature.guard

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.rounded.Visibility
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewire.app.RewireApp
import com.rewire.app.domain.habit.HabitProfile
import com.rewire.app.domain.habit.WarningLevel
import com.rewire.app.rewireViewModel
import com.rewire.app.ui.components.AppIcon
import com.rewire.app.ui.components.SectionTitle
import com.rewire.app.ui.components.formatClock
import com.rewire.app.ui.components.formatMinutes
import com.rewire.app.ui.components.sharedBoundsOrSelf
import com.rewire.app.ui.components.style
import kotlin.math.roundToInt

@Composable
fun HabitDetailScreen(habitId: String, onBack: () -> Unit, onPreview: (String) -> Unit) {
    val vm = rewireViewModel(key = habitId) { HabitDetailViewModel(it, habitId) }
    val profile by vm.habit.collectAsStateWithLifecycle()
    val p = profile ?: return // deleted -> caller already popped
    var pickingApps by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val levelStyle = p.level.style()
    val container by animateColorAsState(levelStyle.container, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "c")

    Surface(Modifier.fillMaxSize().sharedBoundsOrSelf("habit-${p.id}"), color = MaterialTheme.colorScheme.surface) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {},
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                    actions = {
                        Switch(
                            checked = p.habit.enabled,
                            onCheckedChange = vm::setEnabled,
                            modifier = Modifier.padding(end = 12.dp).semantics { contentDescription = "Habit enabled" },
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = container),
                )
            },
        ) { padding ->
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), modifier = Modifier.padding(padding)) {
                item {
                    Surface(color = container, contentColor = levelStyle.onContainer, shape = MaterialTheme.shapes.extraLarge.copy(topStart = androidx.compose.foundation.shape.CornerSize(0), topEnd = androidx.compose.foundation.shape.CornerSize(0))) {
                        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 28.dp)) {
                            Icon(levelStyle.icon, contentDescription = null, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(p.habit.name, style = MaterialTheme.typography.displaySmall)
                            Text(
                                "${levelStyle.label} friction · ${p.apps.size} app${if (p.apps.size == 1) "" else "s"}" + if (!p.habit.enabled) " · paused" else "",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        SectionTitle("Friction level")
                        LevelSelector(p.level, vm::setLevel)
                        Text(levelStyle.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                        FilledTonalButton(onClick = { onPreview(p.id) }, modifier = Modifier.padding(top = 12.dp)) {
                            Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Preview ${levelStyle.label} screen")
                        }
                        SectionTitle("Boundaries")
                        BoundariesCard(p, vm)
                        SectionTitle("Protected apps", trailing = {
                            TextButton(onClick = { pickingApps = true }) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Edit")
                            }
                        })
                        if (p.apps.isEmpty()) {
                            Text("No apps yet. Add the apps this habit lives in.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(p.apps, key = { it.packageName }) { app ->
                    val ctx = LocalContext.current
                    val label = remember(app.packageName) { (ctx.applicationContext as RewireApp).container.installedApps.label(app.packageName) }
                    ListItem(
                        headlineContent = { Text(label) },
                        leadingContent = { AppIcon(app.packageName) },
                        trailingContent = { IconButton(onClick = { vm.removeApp(app.packageName) }) { Icon(Icons.Rounded.Close, contentDescription = "Remove $label") } },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.animateItem().padding(horizontal = 4.dp),
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 32.dp).fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Delete habit")
                    }
                }
            }
        }
    }

    if (pickingApps) {
        val selected = remember { mutableStateListOf<String>().apply { addAll(p.apps.map { it.packageName }) } }
        ModalBottomSheet(onDismissRequest = { vm.setApps(selected.toList()); pickingApps = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
                Text("Protected apps", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 12.dp))
                AppPicker(selected, Modifier.weight(1f, fill = false))
                Button(onClick = { vm.setApps(selected.toList()); pickingApps = false }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("Save · ${selected.size}") }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
            title = { Text("Delete “${p.habit.name}”?") },
            text = { Text("Its rules stop immediately. Past history stays in Matrix.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onBack(); vm.delete() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BoundariesCard(p: HabitProfile, vm: HabitDetailViewModel) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            // 0 on slider = no limit
            SliderSetting(
                title = "Daily limit",
                value = p.rule.dailyLimitMinutes ?: 0, range = 0..240, step = 15,
                display = { if (it == 0) "No limit" else formatMinutes(it) },
                onCommit = { vm.setDailyLimit(it.takeIf { v -> v > 0 }) },
            )
            SliderSetting(
                title = "Launch limit",
                value = p.rule.maxLaunches ?: 0, range = 0..30, step = 1,
                display = { if (it == 0) "No limit" else "$it opens / day" },
                onCommit = { vm.setMaxLaunches(it.takeIf { v -> v > 0 }) },
            )
            AnimatedVisibility(p.level == WarningLevel.MAJOR) {
                SliderSetting(
                    title = "Pause before continue",
                    value = p.rule.pauseSeconds, range = 0..30, step = 1,
                    display = { if (it == 0) "Instant" else "${it}s" },
                    onCommit = vm::setPause,
                )
            }
            AllowedWindow(p.rule.allowedStartMinutes, p.rule.allowedEndMinutes, vm::setWindow)
        }
    }
}

@Composable
private fun SliderSetting(title: String, value: Int, range: IntRange, step: Int, display: (Int) -> String, onCommit: (Int) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(display(local.roundToInt()), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = local,
            // Continuous track (no dot per stop — too dense for 30+ stops), snapped to [step].
            onValueChange = { local = ((it / step).roundToInt() * step).toFloat() },
            onValueChangeFinished = { onCommit(local.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            modifier = Modifier.semantics { contentDescription = title },
        )
    }
}

@Composable
private fun AllowedWindow(start: Int?, end: Int?, onChange: (Int?, Int?) -> Unit) {
    var editing by remember { mutableStateOf<Boolean?>(null) } // true = start, false = end
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Allowed window", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (start != null) TextButton(onClick = { onChange(null, null) }) { Text("Clear") }
        }
        Text(
            if (start == null) "Any time. Set a window to only allow use between two times."
            else "Allowed only between these times.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            AssistChip(onClick = { editing = true }, label = { Text("From ${start?.let(::formatClock) ?: "--:--"}") })
            AssistChip(onClick = { editing = false }, label = { Text("To ${end?.let(::formatClock) ?: "--:--"}") })
        }
    }
    val which = editing ?: return
    val initial = (if (which) start else end) ?: if (which) 9 * 60 else 9 * 60 + 30
    val state = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60)
    AlertDialog(
        onDismissRequest = { editing = null },
        title = { Text(if (which) "Allowed from" else "Allowed until") },
        text = { TimePicker(state) },
        confirmButton = {
            TextButton(onClick = {
                val picked = state.hour * 60 + state.minute
                // Choosing one side fills the other with a sensible 30 min window.
                if (which) onChange(picked, end ?: ((picked + 30) % (24 * 60))) else onChange(start ?: ((picked - 30 + 24 * 60) % (24 * 60)), picked)
                editing = null
            }) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
    )
}
