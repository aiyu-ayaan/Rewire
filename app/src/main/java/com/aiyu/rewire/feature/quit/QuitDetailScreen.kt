package com.aiyu.rewire.feature.quit

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.rewire.R
import com.aiyu.rewire.domain.quit.Quit
import com.aiyu.rewire.domain.quit.QuitHabit
import com.aiyu.rewire.ui.components.SectionTitle
import com.aiyu.rewire.ui.components.readableWidth
import com.aiyu.rewire.ui.components.sharedBoundsOrSelf
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * One journey, told encouragingly: the run, the closest milestone, the ladder, why it started, and
 * restarts framed as part of the story. The list card grows into this screen (container transform).
 */
@Composable
fun QuitDetailScreen(id: String, onBack: () -> Unit) {
    val vm = hiltViewModel<QuitViewModel>()
    val data by vm.data.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val h = data?.habits?.find { it.id == id } ?: return // loading, or deleted -> already popped
    var urge by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    val thoughts = stringArrayResource(R.array.quit_thoughts)
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    Surface(Modifier.fillMaxSize().sharedBoundsOrSelf("quit-$id"), color = MaterialTheme.colorScheme.surface) {
        AnimatedContent(urge, transitionSpec = { fadeIn(effects).togetherWith(fadeOut(effects)) }, label = "detailUrge") { riding ->
            if (riding) UrgeScreen(thoughts, onLeave = { urge = false }, onDone = { vm.urgeRidden(); urge = false })
            else Journey(
                h, now,
                onBack = onBack,
                onEdit = { editing = true },
                onDelete = { onBack(); vm.delete(id) },
                onSlip = { vm.slip(id) },
                onUrge = { urge = true },
            )
        }
    }
    // The undo snackbar's "View" opens the history here too while this screen is on display.
    QuitHistory(data, now, open = false, onClose = {}, onUndo = vm::undo, onRedo = vm::redo)
    if (editing) QuitSheet(
        initial = h,
        onDismiss = { editing = false },
        onSave = { name, reason, start -> vm.edit(id, name, reason, start); editing = false },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Journey(h: QuitHabit, now: Long, onBack: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onSlip: () -> Unit, onUrge: () -> Unit) {
    val days = Quit.runDays(h, now)
    val m = Quit.milestone(days)
    val run = Quit.runMillis(h, now)
    val progress by animateFloatAsState(m.progress, MaterialTheme.motionScheme.slowSpatialSpec(), label = "journeyRing")
    // Toward the next milestone counted in time, so day 0 still moves visibly.
    val toNext = (m.next * Quit.DAY_MS - run).coerceAtLeast(0)
    val stepProgress = ((run - m.previous * Quit.DAY_MS).toFloat() / ((m.next - m.previous) * Quit.DAY_MS)).coerceIn(0f, 1f)
    val cheer = stringArrayResource(R.array.quit_cheer).getOrNull(Quit.stage(days)).orEmpty()
    val date = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().format(date)
    val c = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    var confirmSlip by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(h.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                subtitle = { Text(stringResource(R.string.quit_started_on, day(h.startedAt))) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.warning_back)) } },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.quit_more)) }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.quit_edit)) }, onClick = { menu = false; onEdit() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.common_delete)) }, onClick = { menu = false; confirmDelete = true })
                        }
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).readableWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp),
        ) {
            // Hero: the run, and a line that meets the user where they are.
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = c.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val ringDesc = stringResource(R.string.quit_ring_desc, days, (m.progress * 100).toInt(), m.next)
                    Box(Modifier.size(184.dp).clearAndSetSemantics { contentDescription = ringDesc }, contentAlignment = Alignment.Center) {
                        CircularWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize())
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$days", style = MaterialTheme.typography.displayLarge, color = c.primary)
                            Text(stringResource(R.string.quit_detail_days_strong), style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Surface(shape = CircleShape, color = c.secondaryContainer, contentColor = c.onSecondaryContainer) {
                        Text(
                            stringResource(R.string.quit_run_today, (run % Quit.DAY_MS / 3_600_000).toInt(), (run / 60_000 % 60).toInt()),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(cheer, style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }

            // Closest goal, counted down in time.
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = c.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = c.primaryContainer, contentColor = c.onPrimaryContainer, modifier = Modifier.size(40.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Flag, contentDescription = null, modifier = Modifier.size(20.dp)) }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.quit_next_title), style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
                            Text(stringResource(R.string.quit_days_short, m.next), style = MaterialTheme.typography.titleLarge)
                        }
                        Text(stringResource(R.string.quit_to_go, duration(toNext)), style = MaterialTheme.typography.labelLarge, color = c.primary)
                    }
                    LinearWavyProgressIndicator(progress = { stepProgress }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
                }
            }

            // Three numbers, one card.
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = c.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Row(Modifier.height(IntrinsicSize.Min).padding(vertical = 16.dp)) {
                    Stat(stringResource(R.string.quit_stat_best), duration(Quit.bestMillis(h, now)), Modifier.weight(1f))
                    VerticalDivider(Modifier.fillMaxHeight(), color = c.outlineVariant)
                    Stat(stringResource(R.string.quit_milestones), "${Quit.reached(days).size} / ${Quit.MILESTONES.size}", Modifier.weight(1f))
                    VerticalDivider(Modifier.fillMaxHeight(), color = c.outlineVariant)
                    Stat(stringResource(R.string.quit_stat_restarts), "${h.slips.size}", Modifier.weight(1f))
                }
            }

            if (h.reason.isNotBlank()) {
                SectionTitle(stringResource(R.string.quit_why_title))
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = c.tertiaryContainer, contentColor = c.onTertiaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(20.dp)) {
                        Icon(Icons.Rounded.FormatQuote, contentDescription = null, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(h.reason, style = MaterialTheme.typography.titleMedium, fontStyle = FontStyle.Italic)
                    }
                }
            }

            // The ladder: done (filled + check), next (ring of progress), ahead (quiet).
            SectionTitle(stringResource(R.string.quit_milestones))
            FlowRow(
                Modifier.fillMaxWidth(),
                maxItemsInEachRow = 5,
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Quit.MILESTONES.forEach { d -> MilestoneBadge(d, reached = d <= days, next = d == m.next, progress = stepProgress) }
            }

            // Help, right where it is needed.
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = c.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.quit_urge_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.quit_urge_body), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                    Button(onClick = onUrge, modifier = Modifier.align(Alignment.End)) {
                        Icon(Icons.Rounded.Waves, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.quit_urge_start))
                    }
                }
            }

            val ended = Quit.endedRuns(h).take(5)
            if (h.slips.isNotEmpty()) {
                SectionTitle(stringResource(R.string.quit_history_title))
                Text(stringResource(R.string.quit_history_body), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                if (ended.isNotEmpty()) Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = c.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ended.forEach { (at, length) ->
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.quit_history_row, duration(length))) },
                            supportingContent = { Text(day(at)) },
                            leadingContent = { Icon(Icons.Rounded.Replay, contentDescription = null) },
                            colors = ListItemDefaults.colors(containerColor = c.surfaceContainerLow),
                        )
                    }
                }
            }

            // Last and quiet: a slip is possible, never the headline.
            Text(
                stringResource(R.string.quit_slip_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 12.dp),
            )
            OutlinedButton(onClick = { confirmSlip = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(stringResource(R.string.quit_slipped)) }
        }
    }

    if (confirmSlip) AlertDialog(
        onDismissRequest = { confirmSlip = false },
        title = { Text(stringResource(R.string.quit_slip_title)) },
        text = { Text(stringResource(R.string.quit_slip_body)) },
        confirmButton = { TextButton(onClick = { confirmSlip = false; onSlip() }) { Text(stringResource(R.string.quit_slip_confirm)) } },
        dismissButton = { TextButton(onClick = { confirmSlip = false }) { Text(stringResource(R.string.common_cancel)) } },
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.quit_delete_title)) },
        text = { Text(stringResource(R.string.quit_delete_body)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.common_delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun MilestoneBadge(days: Int, reached: Boolean, next: Boolean, progress: Float) {
    val c = MaterialTheme.colorScheme
    val label = stringResource(R.string.quit_days_short, days)
    val desc = stringResource(if (reached) R.string.quit_milestone_reached else R.string.quit_milestone_ahead, label)
    Box(Modifier.size(56.dp).clearAndSetSemantics { contentDescription = desc }, contentAlignment = Alignment.Center) {
        when {
            reached -> Surface(shape = CircleShape, color = c.primary, contentColor = c.onPrimary, modifier = Modifier.fillMaxSize()) {
                Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(label, style = MaterialTheme.typography.labelMedium)
                }
            }
            next -> {
                CircularProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize(), strokeWidth = 4.dp, trackColor = c.surfaceContainerHighest)
                Text(label, style = MaterialTheme.typography.labelLarge, color = c.primary)
            }
            else -> Surface(shape = CircleShape, color = c.surfaceContainerHigh, contentColor = c.onSurfaceVariant, border = BorderStroke(1.dp, c.outlineVariant), modifier = Modifier.fillMaxSize()) {
                Box(contentAlignment = Alignment.Center) { Text(label, style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}
