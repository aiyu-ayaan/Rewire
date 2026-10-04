package com.aiyu.rewire.feature.quit

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aiyu.rewire.R
import com.aiyu.rewire.domain.quit.Quit
import com.aiyu.rewire.domain.quit.QuitHabit
import com.aiyu.rewire.ui.components.readableWidth
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * One journey, told encouragingly: the live run, the next milestone, the ladder of milestones,
 * why it started, and restarts framed as part of the story. Slipping lives here, below the good news.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuitDetailScreen(
    h: QuitHabit,
    now: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSlip: () -> Unit,
    onUrge: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val days = Quit.runDays(h, now)
    val m = Quit.milestone(days)
    val progress by animateFloatAsState(m.progress, MaterialTheme.motionScheme.slowSpatialSpec(), label = "detailRing")
    val cheer = stringArrayResource(R.array.quit_cheer).getOrNull(Quit.stage(days)).orEmpty()
    val date = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().format(date)
    var menu by remember { mutableStateOf(false) }
    var confirmSlip by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().statusBarsPadding().readableWidth(720.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.quit_back)) }
            Text(h.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, modifier = Modifier.weight(1f).semantics { heading() })
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.quit_more)) }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.quit_edit)) }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.common_delete)) }, onClick = { menu = false; confirmDelete = true })
                }
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Hero: the number that matters, big, with a line that meets the user where they are.
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = colors.secondaryContainer, contentColor = colors.onSecondaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val ringDesc = stringResource(R.string.quit_ring_desc, days, (m.progress * 100).toInt(), m.next)
                    Box(Modifier.size(220.dp).clearAndSetSemantics { contentDescription = ringDesc }, contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 14.dp,
                            color = colors.primary,
                            trackColor = colors.onSecondaryContainer.copy(alpha = 0.12f),
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$days", style = MaterialTheme.typography.displayLarge)
                            Text(stringResource(R.string.quit_detail_days_strong), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    Text(
                        stringResource(R.string.quit_detail_running, duration(Quit.runMillis(h, now))),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    Text(cheer, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                }
            }

            // Next milestone: the goal that is closest, not the far one.
            Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.quit_next_title), style = MaterialTheme.typography.labelLarge, color = colors.primary)
                    Text(stringResource(R.string.quit_to_go, stringResource(R.string.quit_days_short, m.next - days), stringResource(R.string.quit_days_short, m.next)), style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp), trackColor = colors.surfaceContainerHighest)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat(stringResource(R.string.quit_stat_best), duration(Quit.bestMillis(h, now)), Modifier.weight(1f))
                Stat(stringResource(R.string.quit_stat_restarts), "${h.slips.size}", Modifier.weight(1f))
                Stat(stringResource(R.string.quit_stat_started), day(h.startedAt), Modifier.weight(1f))
            }

            SectionHeading(stringResource(R.string.quit_milestones))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Quit.MILESTONES.forEach { d ->
                    val reached = d <= days
                    Surface(
                        shape = CircleShape,
                        color = if (reached) colors.tertiaryContainer else colors.surfaceContainerHigh,
                        contentColor = if (reached) colors.onTertiaryContainer else colors.onSurfaceVariant,
                    ) {
                        val label = stringResource(if (reached) R.string.quit_milestone_reached else R.string.quit_milestone_ahead, stringResource(R.string.quit_days_short, d))
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).semantics(mergeDescendants = true) { contentDescription = label }, verticalAlignment = Alignment.CenterVertically) {
                            // Icon plus text: reached never relies on colour alone.
                            if (reached) { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
                            Text(stringResource(R.string.quit_days_short, d), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            if (h.reason.isNotBlank()) {
                Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = colors.tertiaryContainer, contentColor = colors.onTertiaryContainer), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.FormatQuote, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.quit_why_title), style = MaterialTheme.typography.labelLarge)
                        }
                        Text(h.reason, style = MaterialTheme.typography.titleMedium, fontStyle = FontStyle.Italic)
                    }
                }
            }

            FilledTonalButton(onClick = onUrge, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Waves, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.quit_urge_start))
            }

            if (h.slips.isNotEmpty()) {
                SectionHeading(stringResource(R.string.quit_history_title))
                Text(stringResource(R.string.quit_history_body), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                Quit.endedRuns(h).take(5).forEach { (at, length) ->
                    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(day(at), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant, modifier = Modifier.width(120.dp))
                        Text(stringResource(R.string.quit_history_row, duration(length)), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Last and quiet: a slip is possible, never the headline.
            Text(stringResource(R.string.quit_slip_hint), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            TextButton(onClick = { confirmSlip = true }) { Text(stringResource(R.string.quit_slipped)) }
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
private fun SectionHeading(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp).semantics { heading() })

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), modifier = modifier) {
        Column(Modifier.padding(12.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall)
        }
    }
}
