package com.rewire.app.feature.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewire.app.BuildConfig
import com.rewire.app.core.update.UpdateState
import com.rewire.app.domain.update.UpdateChannel
import com.rewire.app.ui.components.InnerScreen
import com.rewire.app.ui.components.SectionTitle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Profile → Updates: what's running, what's available, how often to look, and which releases to accept. */
@Composable
fun UpdateScreen(onBack: () -> Unit) {
    val vm = hiltViewModel<UpdateViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    DisposableEffect(Unit) {
        vm.offerShownInline.value = true
        onDispose { vm.offerShownInline.value = false }
    }
    val c = MaterialTheme.colorScheme
    val busy = state is UpdateState.Checking || state is UpdateState.Downloading
    val channel = s.updateChannel ?: vm.channel

    InnerScreen(title = "Updates", subtitle = "Rewire ${vm.installedName} · ${if (BuildConfig.ACCESSIBILITY) "Full" else "Lite"}", onBack = onBack) {
        Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = c.surfaceContainerLow)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (val st = state) {
                    is UpdateState.Available, is UpdateState.Downloading, is UpdateState.Ready -> UpdateOffer(st, vm)
                    UpdateState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularWavyProgressIndicator(Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Checking GitHub for a newer release…", style = MaterialTheme.typography.bodyMedium)
                    }
                    UpdateState.UpToDate -> Text("You're up to date.", style = MaterialTheme.typography.titleMedium)
                    is UpdateState.Failed -> Text(st.message, style = MaterialTheme.typography.bodyMedium, color = c.error)
                    UpdateState.Idle -> Text(
                        if (s.updateLastChecked > 0) "Last checked ${formatWhen(s.updateLastChecked)}." else "Not checked yet.",
                        style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant,
                    )
                }
                Button(onClick = vm::check, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Check for updates") }
            }
        }

        SectionTitle("Automatic checks", Modifier.fillMaxWidth())
        Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = c.surfaceContainerLow)) {
            ListItem(
                headlineContent = { Text("Check automatically") },
                supportingContent = {
                    Text("Looks for a newer release on GitHub when you open Rewire and once a day on Wi-Fi. Only the public release list is read; nothing about you or your usage is sent.")
                },
                trailingContent = { Switch(s.updatesEnabled, vm::setEnabled) },
                colors = ListItemDefaults.colors(containerColor = c.surfaceContainerLow),
            )
        }

        SectionTitle("Release channel", Modifier.fillMaxWidth())
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            UpdateChannel.entries.forEachIndexed { i, ch ->
                SegmentedButton(
                    selected = channel == ch,
                    onClick = { vm.setChannel(ch) },
                    shape = SegmentedButtonDefaults.itemShape(i, UpdateChannel.entries.size),
                ) { Text(ch.label) }
            }
        }
        Text(channel.detail, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 6.dp))
        Spacer(Modifier.height(8.dp))
    }
}

private val whenFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
private fun formatWhen(at: Long): String = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(whenFormat)
