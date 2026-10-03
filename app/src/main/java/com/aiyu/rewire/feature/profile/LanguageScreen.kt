package com.aiyu.rewire.feature.profile

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.aiyu.rewire.R
import com.aiyu.rewire.core.settings.AppLocale
import com.aiyu.rewire.ui.components.InnerScreen

/** Picks the app language; "" = follow the system. A full page (or a detail pane on tablets), not a dialog. */
@Composable
fun LanguageScreen(onBack: () -> Unit) {
    val activity = LocalActivity.current
    val current = AppLocale.current(LocalContext.current)
    val container = MaterialTheme.colorScheme.surfaceContainerLow
    InnerScreen(title = stringResource(R.string.profile_language), onBack = onBack) {
        Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = container)) {
            (listOf("") + AppLocale.tags).forEach { tag ->
                ListItem(
                    headlineContent = { Text(if (tag.isEmpty()) stringResource(R.string.profile_language_system) else AppLocale.nativeName(tag)) },
                    trailingContent = { RadioButton(selected = tag == current, onClick = null) },
                    colors = ListItemDefaults.colors(containerColor = container),
                    modifier = Modifier.selectable(selected = tag == current, role = Role.RadioButton) {
                        if (tag != current) activity?.let { AppLocale.set(it, tag) }
                    },
                )
            }
        }
    }
}
