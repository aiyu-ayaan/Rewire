package com.rewire.app.feature.focus

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewire.app.RewireApp
import com.rewire.app.domain.focus.FocusSession
import com.rewire.app.domain.focus.FocusSessionStatus
import com.rewire.app.domain.focus.FocusState
import com.rewire.app.ui.components.EmptyState
import com.rewire.app.ui.components.InnerScreen
import com.rewire.app.ui.components.formatMinutes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private enum class HistoryFilter(val label: String) { ALL("All"), COMPLETED("Completed"), STOPPED("Stopped") }

/** Every finished focus session, newest first, with its outcome and optional achievement note. */
@Composable
fun FocusHistoryScreen(onBack: () -> Unit) {
    val repo = (LocalContext.current.applicationContext as RewireApp).container.focusSessions
    val sessions by remember(repo) { repo.history }.collectAsStateWithLifecycle(initialValue = null)
    var filter by rememberSaveable { mutableStateOf(HistoryFilter.ALL) }
    var editing by remember { mutableStateOf<FocusSession?>(null) }

    val all = sessions.orEmpty()
    val completed = all.count { it.state.status == FocusSessionStatus.COMPLETED }
    InnerScreen(
        title = "Focus history",
        subtitle = if (all.isEmpty()) null else "$completed of ${all.size} completed · ${formatFocused(all.sumOf { it.focusedMillis })} focused",
        onBack = onBack,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HistoryFilter.entries.forEach { f ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
            }
        }
        Spacer(Modifier.height(12.dp))
        val shown = all.filter {
            when (filter) {
                HistoryFilter.ALL -> true
                HistoryFilter.COMPLETED -> it.state.status == FocusSessionStatus.COMPLETED
                HistoryFilter.STOPPED -> it.state.status == FocusSessionStatus.CANCELLED
            }
        }
        when {
            sessions == null -> Unit // first DB read, a few ms
            shown.isEmpty() -> EmptyState(
                title = if (all.isEmpty()) "No sessions yet" else "Nothing here",
                body = if (all.isEmpty()) "Finished and stopped focus sessions land here, with what you achieved." else "No ${filter.label.lowercase()} sessions yet.",
                modifier = Modifier.padding(top = 32.dp),
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                shown.forEach { s -> SessionCard(s, onEditNote = { editing = s }) }
            }
        }
    }

    editing?.let { s ->
        AchievementDialog(
            initial = s.note.orEmpty(),
            completed = s.state.status == FocusSessionStatus.COMPLETED,
            onSave = { repo.setNote(s.id, it); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun SessionCard(s: FocusSession, onEditNote: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val done = s.state.status == FocusSessionStatus.COMPLETED
    val cfg = s.state.config
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = c.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Edit achievement", role = Role.Button, onClick = onEditNote),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = if (done) c.primaryContainer else c.errorContainer) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (done) Icons.Rounded.CheckCircle else Icons.Rounded.StopCircle, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text(if (done) "Completed" else "Stopped", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(formatWhen(s.state.startedAt), style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            Text(formatFocused(s.focusedMillis) + " focused", style = MaterialTheme.typography.titleLarge)
            Text(
                "${if (done) cfg.cycles else s.state.cycle - 1} of ${cfg.cycles} blocks · ${formatFocused(cfg.focusMillis)} + ${formatFocused(cfg.breakMillis)} break" +
                    (s.state.completedAt?.let { end -> s.state.startedAt?.let { " · ${formatFocused(end - it)} total" } } ?: ""),
                style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.EditNote, contentDescription = null, tint = c.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(
                    s.note ?: "Add what you achieved",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (s.note == null) c.onSurfaceVariant else c.onSurface,
                )
            }
        }
    }
}

/** Optional "what did you achieve?" note. Empty save clears it; skipping is always fine. */
@Composable
fun AchievementDialog(initial: String, completed: Boolean, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.EditNote, contentDescription = null) },
        title = { Text(if (completed) "What did you achieve?" else "What did you get done?") },
        text = {
            Column {
                Text(
                    "Optional. A line for future you, shown in Focus history.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= MAX_NOTE) text = it },
                    placeholder = { Text("Finished the chapter 3 draft") },
                    minLines = 2,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    supportingText = { Text("${text.length} / $MAX_NOTE") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (initial.isEmpty()) "Skip" else "Cancel") } },
    )
}

private const val MAX_NOTE = 280

/** Minutes for real sessions; seconds only show for the sub-minute debug quick test. */
private fun formatFocused(millis: Long): String =
    if (millis == 0L) "0m" else if (millis < FocusState.MINUTE) "${millis / 1000}s" else formatMinutes((millis / FocusState.MINUTE).toInt())

private val whenFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

private fun formatWhen(at: Long?): String =
    at?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(whenFormat) }.orEmpty()
