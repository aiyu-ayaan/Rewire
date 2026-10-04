package com.aiyu.rewire.feature.quit

import androidx.activity.compose.BackHandler
import com.aiyu.rewire.ui.LocalBottomBarInsets
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.rewire.R
import com.aiyu.rewire.domain.quit.Quit
import com.aiyu.rewire.domain.quit.QuitHabit
import com.aiyu.rewire.ui.HideNavigationBar
import com.aiyu.rewire.ui.components.EmptyState
import com.aiyu.rewire.ui.components.MorphingShape
import com.aiyu.rewire.ui.components.SectionTitle
import com.aiyu.rewire.ui.components.readableWidth
import com.aiyu.rewire.ui.components.sharedBoundsOrSelf
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Urge session length and breathing rhythm (in 4 s, hold 4 s, out 6 s). */
private const val URGE_SECONDS = 120
private const val BREATH_IN = 4
private const val BREATH_HOLD = 4
private const val BREATH_CYCLE = 14

@Composable
fun QuitScreen(onOpen: (String) -> Unit) {
    val vm = hiltViewModel<QuitViewModel>()
    val data by vm.data.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    var urge by rememberSaveable { mutableStateOf(false) }
    // null = closed, "" = new tracker, otherwise the id being edited.
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    val thoughts = stringArrayResource(R.array.quit_thoughts)

    // Breathing is a step inside the tab, like the running focus timer: the bar steps away.
    HideNavigationBar(hide = urge)
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedContent(urge, transitionSpec = { fadeIn(effects).togetherWith(fadeOut(effects)) }, label = "urge") { riding ->
        if (riding) {
            UrgeScreen(thoughts, onLeave = { urge = false }, onDone = { vm.urgeRidden(); urge = false })
        } else {
            val d = data ?: return@AnimatedContent
            QuitHome(
                habits = d.habits,
                urgesRidden = d.urgesRidden,
                now = now,
                thoughts = thoughts,
                onUrge = { urge = true },
                onAdd = { sheet = "" },
                onOpen = onOpen,
                onEdit = { sheet = it },
                onDelete = vm::delete,
            )
        }
    }

    sheet?.let { id ->
        val editing = data?.habits?.find { it.id == id }
        QuitSheet(
            initial = editing,
            onDismiss = { sheet = null },
            onSave = { name, reason, start ->
                if (editing == null) vm.add(name, reason, start) else vm.edit(editing.id, name, reason, start)
                sheet = null
            },
        )
    }
}

@Composable
private fun QuitHome(
    habits: List<QuitHabit>,
    urgesRidden: Int,
    now: Long,
    thoughts: Array<String>,
    onUrge: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val list = rememberLazyGridState()
    val fabExpanded by remember { derivedStateOf { list.firstVisibleItemIndex == 0 } }
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(340.dp),
            state = list,
            modifier = Modifier.fillMaxSize().statusBarsPadding().readableWidth(1200.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp + LocalBottomBarInsets.current.content),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val full: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
            item(span = full) { Header() }
            item(span = full) { ThoughtCard(thoughts) }
            item(span = full) { UrgeCard(urgesRidden, onUrge) }
            item(span = full) { SectionTitle(stringResource(R.string.quit_section), trailing = { if (habits.isNotEmpty()) Text("${habits.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }) }
            if (habits.isEmpty()) {
                item(span = full) {
                    EmptyState(
                        title = stringResource(R.string.quit_empty_title),
                        body = stringResource(R.string.quit_empty_body),
                        action = { FilledTonalButton(onClick = onAdd) { Text(stringResource(R.string.quit_add_first)) } },
                    )
                }
            }
            items(habits, key = { it.id }) { h ->
                QuitCard(h, now, onOpen = { onOpen(h.id) }, onEdit = { onEdit(h.id) }, onDelete = { onDelete(h.id) }, modifier = Modifier.animateItem().padding(bottom = 12.dp))
            }
            item(span = full) { PrivateNote() }
        }
        if (habits.isNotEmpty()) ExtendedFloatingActionButton(
            onClick = onAdd,
            expanded = fabExpanded,
            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.quit_new)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).padding(bottom = LocalBottomBarInsets.current.fab),
        )
    }
}

@Composable
private fun Header() {
    Row(Modifier.padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        MorphingShape(brush = SolidColor(MaterialTheme.colorScheme.tertiaryContainer), modifier = Modifier.size(44.dp), rotationMillis = 60_000)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(stringResource(R.string.nav_quit), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.quit_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ThoughtCard(thoughts: Array<String>) {
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val today = remember { LocalDate.now().toEpochDay() }
    val text = thoughts.getOrNull(Quit.thoughtIndex(today, offset, thoughts.size)).orEmpty()
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.FormatQuote, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.quit_thought_title), style = MaterialTheme.typography.labelLarge)
            }
            AnimatedContent(text, label = "thought") { t ->
                Text(t, style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth().padding(top = 12.dp, end = 8.dp).semantics { liveRegion = LiveRegionMode.Polite })
            }
            TextButton(onClick = { offset++ }, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.quit_thought_another)) }
        }
    }
}

@Composable
private fun UrgeCard(urgesRidden: Int, onUrge: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.quit_urge_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.quit_urge_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (urgesRidden > 0) Text(pluralStringResource(R.plurals.quit_urges_ridden, urgesRidden, urgesRidden), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.weight(1f))
                else Spacer(Modifier.weight(1f))
                Button(onClick = onUrge) {
                    Icon(Icons.Rounded.Waves, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.quit_urge_start))
                }
            }
        }
    }
}

@Composable
private fun QuitCard(h: QuitHabit, now: Long, onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val days = Quit.runDays(h, now)
    val m = Quit.milestone(days)
    val progress by animateFloatAsState(m.progress, MaterialTheme.motionScheme.slowSpatialSpec(), label = "milestone")
    val rest = Quit.runMillis(h, now) % Quit.DAY_MS
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Card(
        onClick = onOpen,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier.fillMaxWidth().sharedBoundsOrSelf("quit-${h.id}"),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val ringDesc = stringResource(R.string.quit_ring_desc, days, (m.progress * 100).toInt(), m.next)
                Box(Modifier.size(88.dp).clearAndSetSemantics { contentDescription = ringDesc }, contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 8.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$days", style = MaterialTheme.typography.headlineMedium)
                        Text(pluralStringResource(R.plurals.quit_days_word, days), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(h.name, style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.quit_run_today, (rest / 3_600_000).toInt(), (rest / 60_000 % 60).toInt()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.quit_best, duration(Quit.bestMillis(h, now))), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.quit_next, stringResource(R.string.quit_days_short, m.next)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.quit_more)) }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.quit_edit)) }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.common_delete)) }, onClick = { menu = false; confirmDelete = true })
                    }
                }
            }
            if (h.reason.isNotBlank()) {
                Text(h.reason, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, modifier = Modifier.padding(top = 12.dp, end = 12.dp))
            }
            val reached = Quit.reached(days)
            if (reached.isNotEmpty()) {
                FlowRow(Modifier.padding(top = 12.dp, end = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    reached.forEach { d ->
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
                            Text(stringResource(R.string.quit_days_short, d), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.quit_delete_title)) },
        text = { Text(stringResource(R.string.quit_delete_body)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.common_delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
internal fun duration(ms: Long): String {
    val days = (ms / Quit.DAY_MS).toInt()
    val hours = (ms % Quit.DAY_MS / 3_600_000).toInt()
    return if (days > 0) stringResource(R.string.quit_days_hours, days, hours)
    else stringResource(R.string.quit_hours_minutes, hours, (ms / 60_000 % 60).toInt())
}

@Composable
private fun PrivateNote() {
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.quit_private_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- Add / edit ---------------------------------------------------------------------------------

@Composable
internal fun QuitSheet(initial: QuitHabit?, onDismiss: () -> Unit, onSave: (name: String, reason: String, startedAt: Long) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var reason by rememberSaveable { mutableStateOf(initial?.reason.orEmpty()) }
    // null = right now; editing keeps the existing start unless changed.
    var start by rememberSaveable { mutableStateOf(initial?.startedAt) }
    var picking by remember { mutableStateOf(false) }
    val tooLong = name.trim().length > Quit.MAX_NAME

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(if (initial == null) R.string.quit_new else R.string.quit_edit_title), style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.quit_name)) },
                supportingText = { Text(if (tooLong) "${name.trim().length} / ${Quit.MAX_NAME}" else stringResource(R.string.quit_name_hint)) },
                isError = tooLong,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = reason,
                onValueChange = { if (it.length <= Quit.MAX_REASON) reason = it },
                label = { Text(stringResource(R.string.quit_reason)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.quit_started), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = start == null, onClick = { start = null }, label = { Text(stringResource(R.string.quit_started_now)) })
                FilterChip(
                    selected = start != null,
                    onClick = { picking = true },
                    label = {
                        Text(start?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }
                            ?: stringResource(R.string.quit_started_pick))
                    },
                )
            }
            Button(
                onClick = { onSave(name, reason, start ?: System.currentTimeMillis()) },
                enabled = Quit.isValidName(name),
                modifier = Modifier.align(Alignment.End),
            ) { Text(stringResource(R.string.common_save)) }
        }
    }

    if (picking) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (start?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                // The picker speaks UTC midnights; no future starts.
                override fun isSelectableDate(utcTimeMillis: Long) = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() <= today
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { utc ->
                        val day = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()
                        start = if (day == today) null else day.atStartOfDay(zone).toInstant().toEpochMilli()
                    }
                    picking = false
                }) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.common_cancel)) } },
        ) { DatePicker(state) }
    }
}

// ---- Ride the wave ------------------------------------------------------------------------------

/** Two minutes of paced breathing with a thought per breath; finishing counts one urge ridden out. */
@Composable
internal fun UrgeScreen(thoughts: Array<String>, onLeave: () -> Unit, onDone: () -> Unit) {
    var elapsed by rememberSaveable { mutableIntStateOf(0) }
    val first = rememberSaveable { (thoughts.indices).randomOrNull() ?: 0 }
    val ideas = stringArrayResource(R.array.quit_urge_ideas)
    val idea = rememberSaveable { ideas.indices.randomOrNull() ?: 0 }
    LaunchedEffect(Unit) { while (elapsed < URGE_SECONDS) { delay(1_000); elapsed++ } }
    BackHandler(onBack = onLeave)

    val done = elapsed >= URGE_SECONDS
    val phase = elapsed % BREATH_CYCLE
    val (label, target, millis) = when {
        done -> Triple(R.string.quit_urge_done_title, 0.8f, 1_000)
        phase < BREATH_IN -> Triple(R.string.quit_breathe_in, 1f, BREATH_IN * 1_000)
        phase < BREATH_IN + BREATH_HOLD -> Triple(R.string.quit_breathe_hold, 1f, BREATH_HOLD * 1_000)
        else -> Triple(R.string.quit_breathe_out, 0.55f, (BREATH_CYCLE - BREATH_IN - BREATH_HOLD) * 1_000)
    }
    val scale by animateFloatAsState(target, tween(millis), label = "breath")
    val thought = thoughts.getOrNull(((first + elapsed / BREATH_CYCLE) % thoughts.size.coerceAtLeast(1))).orEmpty()

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp).readableWidth(560.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
            MorphingShape(
                brush = SolidColor(MaterialTheme.colorScheme.tertiaryContainer),
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale },
                rotationMillis = 60_000,
            )
            Text(stringResource(label), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onTertiaryContainer, textAlign = TextAlign.Center, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        if (done) {
            Text(stringResource(R.string.quit_urge_done_body), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(stringResource(R.string.quit_urge_idea_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(ideas.getOrNull(idea).orEmpty(), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Button(onClick = onDone) { Text(stringResource(R.string.quit_urge_done)) }
        } else {
            val left = URGE_SECONDS - elapsed
            Text(stringResource(R.string.quit_urge_left, left / 60, left % 60), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AnimatedContent(thought, label = "urgeThought") { t ->
                Text(t, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            TextButton(onClick = onLeave) { Text(stringResource(R.string.quit_urge_leave)) }
        }
    }
}
