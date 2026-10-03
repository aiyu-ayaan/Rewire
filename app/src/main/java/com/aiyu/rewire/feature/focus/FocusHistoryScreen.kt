package com.aiyu.rewire.feature.focus

import androidx.compose.foundation.verticalScroll

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.clickable
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.aiyu.rewire.R
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiyu.rewire.data.FocusSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import com.aiyu.rewire.domain.focus.FocusSession
import com.aiyu.rewire.domain.focus.FocusSessionStatus
import com.aiyu.rewire.domain.focus.FocusState
import com.aiyu.rewire.ui.components.EmptyState
import com.aiyu.rewire.ui.components.InnerScreen
import com.aiyu.rewire.ui.components.formatMinutes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@HiltViewModel
class FocusHistoryViewModel @Inject constructor(private val sessions: FocusSessionRepository) : ViewModel() {
    /** null until the first Room read lands. */
    val history: StateFlow<List<FocusSession>?> = sessions.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun setNote(id: String, note: String) = sessions.setNote(id, note)
}

private enum class HistoryFilter(@StringRes val label: Int) { ALL(R.string.focus_filter_all), COMPLETED(R.string.focus_completed), STOPPED(R.string.focus_stopped) }

/** Every finished focus session, newest first, with its outcome and optional achievement note. */
@Composable
fun FocusHistoryScreen(onBack: () -> Unit) {
    val vm = hiltViewModel<FocusHistoryViewModel>()
    val sessions by vm.history.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(HistoryFilter.ALL) }
    var editing by remember { mutableStateOf<FocusSession?>(null) }

    val all = sessions.orEmpty()
    val completed = all.count { it.state.status == FocusSessionStatus.COMPLETED }
    InnerScreen(
        title = stringResource(R.string.focus_history),
        subtitle = if (all.isEmpty()) null else stringResource(R.string.focus_history_subtitle, completed, all.size, formatFocused(all.sumOf { it.focusedMillis })),
        onBack = onBack,
        maxWidth = 960.dp,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HistoryFilter.entries.forEach { f ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(stringResource(f.label)) })
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
                title = stringResource(if (all.isEmpty()) R.string.focus_history_empty_title else R.string.focus_history_filtered_title),
                body = if (all.isEmpty()) stringResource(R.string.focus_history_empty_body) else stringResource(R.string.focus_history_filtered_body, stringResource(filter.label).lowercase()),
                modifier = Modifier.padding(top = 32.dp),
            )
            // Cards sit side by side once the window is wide (InnerScreen caps width, so this only matters beyond ~640dp).
            else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = if (LocalConfiguration.current.screenWidthDp >= 840) 2 else 1) {
                shown.forEach { s -> SessionCard(s, onEditNote = { editing = s }, modifier = Modifier.weight(1f)) }
            }
        }
    }

    editing?.let { s ->
        AchievementDialog(
            initial = s.note.orEmpty(),
            completed = s.state.status == FocusSessionStatus.COMPLETED,
            onSave = { vm.setNote(s.id, it); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun SessionCard(s: FocusSession, onEditNote: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val done = s.state.status == FocusSessionStatus.COMPLETED
    val cfg = s.state.config
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = c.surfaceContainerLow),
        modifier = modifier.fillMaxWidth().clickable(onClickLabel = stringResource(R.string.focus_edit_achievement), role = Role.Button, onClick = onEditNote),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = if (done) c.primaryContainer else c.errorContainer) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (done) Icons.Rounded.CheckCircle else Icons.Rounded.StopCircle, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text(stringResource(if (done) R.string.focus_completed else R.string.focus_stopped), style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(formatWhen(s.state.startedAt), style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.focus_focused_fmt, formatFocused(s.focusedMillis)), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.focus_session_detail, if (done) cfg.cycles else s.state.cycle - 1, cfg.cycles, formatFocused(cfg.focusMillis), formatFocused(cfg.breakMillis)) +
                    (s.state.completedAt?.let { end -> s.state.startedAt?.let { stringResource(R.string.focus_session_total, formatFocused(end - it)) } } ?: ""),
                style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.EditNote, contentDescription = null, tint = c.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(
                    s.note ?: stringResource(R.string.focus_add_achievement),
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
        title = { Text(stringResource(if (completed) R.string.focus_achieve_title else R.string.focus_achieve_title_stopped)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.focus_achieve_hint),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= MAX_NOTE) text = it },
                    placeholder = { Text(stringResource(R.string.focus_achieve_placeholder)) },
                    minLines = 2,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    supportingText = { Text(stringResource(R.string.focus_char_count, text.length, MAX_NOTE)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.common_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(if (initial.isEmpty()) R.string.focus_skip else R.string.common_cancel)) } },
    )
}

private const val MAX_NOTE = 280

/** Minutes for real sessions; seconds only show for the sub-minute debug quick test. */
private fun formatFocused(millis: Long): String =
    if (millis == 0L) "0m" else if (millis < FocusState.MINUTE) "${millis / 1000}s" else formatMinutes((millis / FocusState.MINUTE).toInt())

private val whenFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

private fun formatWhen(at: Long?): String =
    at?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(whenFormat) }.orEmpty()
