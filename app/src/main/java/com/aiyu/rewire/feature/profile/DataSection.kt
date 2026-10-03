package com.aiyu.rewire.feature.profile

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.rewire.R
import com.aiyu.rewire.ui.components.SectionTitle
import java.time.LocalDate

/** Export / import / clear through the system file picker (no storage permission needed). */
@Composable
fun DataSection(vm: DataViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm::export) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::readImport) }

    state.message?.let { m ->
        val text = stringResource(m.stringRes())
        LaunchedEffect(m) {
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
            vm.messageShown()
        }
    }

    SectionTitle(stringResource(R.string.data_section))
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column {
            DataRow(Icons.Rounded.FileUpload, R.string.data_export, R.string.data_export_desc, !state.busy) { export.launch("rewire-${LocalDate.now()}.json") }
            DataRow(Icons.Rounded.FileDownload, R.string.data_import, R.string.data_import_desc, !state.busy) { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
            DataRow(Icons.Rounded.Delete, R.string.data_clear, R.string.data_clear_desc, !state.busy) { vm.requestClear() }
        }
    }

    state.pendingImport?.let { s ->
        AlertDialog(
            onDismissRequest = vm::dismissDialogs,
            title = { Text(stringResource(R.string.data_import_title)) },
            text = { Text(stringResource(R.string.data_import_body, s.habits.size, s.warnings.size, s.events.size, s.focusSessions.size)) },
            confirmButton = { TextButton(vm::confirmImport) { Text(stringResource(R.string.data_import_confirm)) } },
            dismissButton = { TextButton(vm::dismissDialogs) { Text(stringResource(R.string.data_cancel)) } },
        )
    }
    if (state.confirmClear) {
        AlertDialog(
            onDismissRequest = vm::dismissDialogs,
            title = { Text(stringResource(R.string.data_clear_title)) },
            text = { Text(stringResource(R.string.data_clear_body)) },
            confirmButton = { TextButton(vm::confirmClear) { Text(stringResource(R.string.data_clear_confirm)) } },
            dismissButton = { TextButton(vm::dismissDialogs) { Text(stringResource(R.string.data_cancel)) } },
        )
    }
}

@Composable
private fun DataRow(icon: ImageVector, title: Int, subtitle: Int, enabled: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(subtitle)) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

private fun DataMessage.stringRes() = when (this) {
    DataMessage.EXPORTED -> R.string.data_msg_exported
    DataMessage.IMPORTED -> R.string.data_msg_imported
    DataMessage.CLEARED -> R.string.data_msg_cleared
    DataMessage.EXPORT_FAILED -> R.string.data_msg_export_failed
    DataMessage.FILE_UNREADABLE -> R.string.data_msg_file_unreadable
    DataMessage.NOT_A_BACKUP -> R.string.data_msg_not_a_backup
    DataMessage.UNSUPPORTED_VERSION -> R.string.data_msg_unsupported_version
    DataMessage.INCONSISTENT -> R.string.data_msg_inconsistent
    DataMessage.FOCUS_ACTIVE -> R.string.data_msg_focus_active
    DataMessage.IMPORT_FAILED -> R.string.data_msg_import_failed
    DataMessage.CLEAR_FAILED -> R.string.data_msg_clear_failed
}
