package com.aiyu.rewire.feature.update

import androidx.compose.ui.semantics.contentDescription

import androidx.compose.ui.semantics.semantics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.res.stringResource
import com.aiyu.rewire.R
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
import com.aiyu.rewire.BuildConfig
import com.aiyu.rewire.core.update.UpdateState
import com.aiyu.rewire.domain.update.UpdateChannel
import com.aiyu.rewire.ui.components.InnerScreen
import com.aiyu.rewire.ui.components.SectionTitle
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

    InnerScreen(title = stringResource(R.string.profile_updates), subtitle = stringResource(R.string.update_subtitle, vm.installedName, stringResource(if (BuildConfig.ACCESSIBILITY) R.string.about_edition_full else R.string.about_edition_lite)), onBack = onBack) {
        Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = c.surfaceContainerLow)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (val st = state) {
                    is UpdateState.Available, is UpdateState.Downloading, is UpdateState.Ready -> UpdateOffer(st, vm)
                    UpdateState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularWavyProgressIndicator(Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.update_checking), style = MaterialTheme.typography.bodyMedium)
                    }
                    UpdateState.UpToDate -> Text(stringResource(R.string.update_up_to_date), style = MaterialTheme.typography.titleMedium)
                    is UpdateState.Failed -> Text(st.message, style = MaterialTheme.typography.bodyMedium, color = c.error)
                    UpdateState.Idle -> Text(
                        if (s.updateLastChecked > 0) stringResource(R.string.update_last_checked, formatWhen(s.updateLastChecked)) else stringResource(R.string.update_not_checked),
                        style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant,
                    )
                }
                Button(onClick = vm::check, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.update_check)) }
            }
        }

        SectionTitle(stringResource(R.string.update_automatic), Modifier.fillMaxWidth())
        Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = c.surfaceContainerLow)) {
            val autoDesc = stringResource(R.string.update_check_automatically)
            ListItem(
                headlineContent = { Text(stringResource(R.string.update_check_automatically)) },
                supportingContent = {
                    Text(stringResource(R.string.update_automatic_desc))
                },
                trailingContent = { Switch(s.updatesEnabled, vm::setEnabled, modifier = Modifier.semantics { contentDescription = autoDesc }) },
                colors = ListItemDefaults.colors(containerColor = c.surfaceContainerLow),
            )
        }

        SectionTitle(stringResource(R.string.update_channel), Modifier.fillMaxWidth())
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
