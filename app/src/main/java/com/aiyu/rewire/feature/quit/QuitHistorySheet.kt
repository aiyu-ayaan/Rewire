package com.aiyu.rewire.feature.quit

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.rewire.R
import com.aiyu.rewire.core.undo.UndoCenter
import com.aiyu.rewire.domain.quit.Quit
import com.aiyu.rewire.domain.quit.QuitChangeKind
import com.aiyu.rewire.domain.quit.QuitData

internal val QuitChangeKind.message: Int
    get() = when (this) {
        QuitChangeKind.ADDED -> R.string.undo_quit_added
        QuitChangeKind.EDITED -> R.string.undo_quit_edited
        QuitChangeKind.SLIPPED -> R.string.undo_quit_slipped
        QuitChangeKind.DELETED -> R.string.undo_quit_deleted
    }

/** Opens the change history when [open] is set or the undo snackbar's "View" asks for it. */
@Composable
internal fun QuitHistory(data: QuitData?, now: Long, open: Boolean, onClose: () -> Unit, onUndo: (String) -> Unit, onRedo: (String) -> Unit) {
    val requested by UndoCenter.historyRequested.collectAsStateWithLifecycle()
    if ((open || requested) && data != null) {
        QuitHistorySheet(data, now, onUndo, onRedo, onDismiss = { onClose(); UndoCenter.historyShown() })
    }
}

/**
 * ⋮-menu entries for one tracker: undo or redo its latest change, and the full history. Nothing while the
 * history is empty, so the menu stays as it was.
 */
@Composable
internal fun ChangeMenuItems(data: QuitData?, trackerId: String, now: Long, onUndo: (String) -> Unit, onRedo: (String) -> Unit, onHistory: () -> Unit, close: () -> Unit) {
    if (data == null || Quit.recentChanges(data, now).isEmpty()) return
    val last = Quit.lastChange(data, trackerId, now)
    if (last != null && Quit.canUndo(data, last)) {
        DropdownMenuItem(
            text = { Text("${stringResource(R.string.undo_action)} · ${stringResource(last.kind.message)}") },
            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null) },
            onClick = { close(); onUndo(last.id) },
        )
    } else if (last != null && Quit.canRedo(data, last)) {
        DropdownMenuItem(
            text = { Text("${stringResource(R.string.undo_redo)} · ${stringResource(last.kind.message)}") },
            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = null) },
            onClick = { close(); onRedo(last.id) },
        )
    }
    DropdownMenuItem(
        text = { Text(stringResource(R.string.quit_history)) },
        leadingIcon = { Icon(Icons.Rounded.History, contentDescription = null) },
        onClick = { close(); onHistory() },
    )
}

/** Recent tracker changes, newest first, each with its own undo or redo. Kept for 30 minutes, then deleted. */
@Composable
private fun QuitHistorySheet(data: QuitData, now: Long, onUndo: (String) -> Unit, onRedo: (String) -> Unit, onDismiss: () -> Unit) {
    val changes = Quit.recentChanges(data, now)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.quit_history), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 24.dp))
            Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Timer, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.quit_history_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (changes.isEmpty()) {
                Text(stringResource(R.string.quit_history_empty), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(24.dp))
            }
            changes.forEach { c ->
                val name = (c.after ?: c.before)?.name.orEmpty()
                val ago = DateUtils.getRelativeTimeSpanString(c.at, now, DateUtils.MINUTE_IN_MILLIS).toString()
                ListItem(
                    headlineContent = { Text(stringResource(c.kind.message)) },
                    supportingContent = { Text("$name · $ago", maxLines = 1) },
                    trailingContent = {
                        if (c.undone) TextButton(onClick = { onRedo(c.id) }, enabled = Quit.canRedo(data, c)) { Text(stringResource(R.string.undo_redo)) }
                        else TextButton(onClick = { onUndo(c.id) }, enabled = Quit.canUndo(data, c)) { Text(stringResource(R.string.undo_action)) }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}
