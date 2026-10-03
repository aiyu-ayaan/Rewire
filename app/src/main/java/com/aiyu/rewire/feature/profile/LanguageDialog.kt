package com.aiyu.rewire.feature.profile

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.aiyu.rewire.R
import com.aiyu.rewire.core.settings.AppLocale

/** Picks the app language; "" = follow the system. */
@Composable
fun LanguageDialog(current: String, onDismiss: () -> Unit) {
    val activity = LocalActivity.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_language)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                (listOf("") + AppLocale.tags).forEach { tag ->
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(selected = tag == current, role = Role.RadioButton) {
                                onDismiss()
                                if (tag != current) activity?.let { AppLocale.set(it, tag) }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = tag == current, onClick = null)
                        Text(
                            if (tag.isEmpty()) stringResource(R.string.profile_language_system) else AppLocale.nativeName(tag),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
