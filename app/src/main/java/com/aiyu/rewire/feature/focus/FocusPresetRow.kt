package com.aiyu.rewire.feature.focus

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.aiyu.rewire.R
import com.aiyu.rewire.domain.focus.FocusConfig
import com.aiyu.rewire.domain.focus.FocusPreset
import com.aiyu.rewire.domain.focus.FocusPresetError
import com.aiyu.rewire.domain.focus.FocusPresets
import kotlinx.coroutines.launch

private fun FocusPreset.matches(c: FocusConfig) = focusMinutes == c.focusMinutes && breakMinutes == c.breakMinutes && cycles == c.cycles

/**
 * Built-in + user preset chips, "Save as preset", and Rename / Delete for the selected user preset.
 * Presets only fill the draft; starting still goes through the normal Start button.
 */
@Composable
internal fun FocusPresetRow(
    draft: FocusConfig,
    userPresets: List<FocusPreset>,
    onApply: (FocusConfig) -> Unit,
    onSave: suspend (String) -> FocusPresetError?,
    onRename: suspend (String, String) -> FocusPresetError?,
    onDelete: (String) -> Unit,
) {
    var saving by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<FocusPreset?>(null) }
    var deleting by remember { mutableStateOf<FocusPreset?>(null) }

    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        (FocusPresets.builtIn + userPresets).forEach { p ->
            val selected = p.matches(draft)
            val name = when (p.id) {
                "builtin_pomodoro" -> stringResource(R.string.focus_preset_pomodoro)
                "builtin_deep_work" -> stringResource(R.string.focus_preset_deep_work)
                "builtin_sprint" -> stringResource(R.string.focus_preset_sprint)
                else -> p.name
            }
            val desc = stringResource(R.string.focus_preset_chip_desc, name, p.focusMinutes, p.breakMinutes, p.cycles)
            val state = stringResource(if (selected) R.string.focus_preset_selected else R.string.focus_preset_not_selected)
            FilterChip(
                selected = selected,
                onClick = { onApply(p.toConfig()) },
                label = { Text(name) },
                modifier = Modifier.semantics { contentDescription = desc; stateDescription = state },
            )
        }
        val saveDesc = stringResource(R.string.focus_preset_save_desc)
        AssistChip(
            onClick = { saving = true },
            label = { Text(stringResource(R.string.focus_preset_save)) },
            leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            enabled = draft.isValid,
            modifier = Modifier.semantics { contentDescription = saveDesc },
        )
    }

    userPresets.firstOrNull { it.matches(draft) }?.let { p ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val manage = stringResource(R.string.focus_preset_manage, p.name)
            Text(manage, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = { renaming = p }) { Text(stringResource(R.string.focus_preset_rename)) }
            TextButton(onClick = { deleting = p }) { Text(stringResource(R.string.focus_preset_delete)) }
        }
    }

    if (saving) NameDialog(stringResource(R.string.focus_preset_name_title), "", { saving = false }) { onSave(it) }
    renaming?.let { p -> NameDialog(stringResource(R.string.focus_preset_rename_title), p.name, { renaming = null }) { onRename(p.id, it) } }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.focus_preset_delete_title)) },
            text = { Text(stringResource(R.string.focus_preset_delete_body, p.name)) },
            confirmButton = { TextButton(onClick = { onDelete(p.id); deleting = null }) { Text(stringResource(R.string.focus_preset_delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.focus_preset_cancel)) } },
        )
    }
}

/** Closes on success; on a rule violation stays open and says why. */
@Composable
private fun NameDialog(title: String, initial: String, onClose: () -> Unit, submit: suspend (String) -> FocusPresetError?) {
    var name by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<FocusPresetError?>(null) }
    val scope = rememberCoroutineScope()
    val message = error?.let {
        when (it) {
            FocusPresetError.NAME_BLANK -> stringResource(R.string.focus_preset_error_blank)
            FocusPresetError.NAME_TOO_LONG -> stringResource(R.string.focus_preset_error_too_long, FocusPresets.MAX_NAME)
            FocusPresetError.NAME_TAKEN -> stringResource(R.string.focus_preset_error_taken)
            FocusPresetError.TOO_MANY -> stringResource(R.string.focus_preset_error_too_many, FocusPresets.MAX_USER_PRESETS)
            FocusPresetError.INVALID_CONFIG -> stringResource(R.string.focus_preset_error_invalid)
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text(stringResource(R.string.focus_preset_name_label)) },
                    singleLine = true,
                    isError = message != null,
                    supportingText = message?.let { m -> { Text(m) } },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { scope.launch { error = submit(name); if (error == null) onClose() } }) { Text(stringResource(R.string.focus_preset_confirm)) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.focus_preset_cancel)) } },
    )
}
