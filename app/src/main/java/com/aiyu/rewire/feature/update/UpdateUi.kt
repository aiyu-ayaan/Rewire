package com.aiyu.rewire.feature.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.res.stringResource
import com.aiyu.rewire.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.rewire.core.update.UpdateState

/** Shown over the app when the launch / daily check found a newer release. "Not now" is quiet for a day. */
@Composable
fun UpdateHost() {
    val vm = hiltViewModel<UpdateViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    val inline by vm.offerShownInline.collectAsStateWithLifecycle()
    if (!inline && (state is UpdateState.Available || state is UpdateState.Downloading || state is UpdateState.Ready)) {
        // Fully expanded: a half-open sheet hides the buttons under the system gesture bar.
        ModalBottomSheet(onDismissRequest = vm::dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            UpdateOffer(state, vm, Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp).navigationBarsPadding())
        }
    }
}

/** The body shared by the sheet and the Updates screen: what's new, then the one action that fits the state. */
@Composable
fun UpdateOffer(state: UpdateState, vm: UpdateViewModel, modifier: Modifier = Modifier) {
    val release = when (state) {
        is UpdateState.Available -> state.release
        is UpdateState.Downloading -> state.release
        is UpdateState.Ready -> state.release
        else -> return
    }
    val context = LocalContext.current
    // "Install unknown apps" is granted in system settings; re-check when the user comes back.
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { resumes++; onPauseOrDispose { } }
    val canInstall = remember(resumes, state) { vm.canInstall() }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(stringResource(R.string.notif_update_title, release.name), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.update_youre_on, vm.installedName), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (release.notes.isNotBlank()) {
            ReleaseNotesView(release.notes, Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()))
        }
        when (state) {
            is UpdateState.Available -> Button(onClick = { vm.download(state) }, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.update_download) + if (state.apk.size > 0) stringResource(R.string.update_download_size, state.apk.size / 1_000_000) else "")
            }
            is UpdateState.Downloading -> {
                LinearWavyProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.update_downloading, (state.progress * 100).toInt()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is UpdateState.Ready -> if (canInstall) {
                Button(onClick = { vm.install(state) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.update_install)) }
            } else {
                Text(
                    stringResource(R.string.update_install_permission),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { context.startActivity(vm.installPermissionIntent()) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.update_allow_installs)) }
            }
            else -> Unit
        }
        if (state !is UpdateState.Downloading) OutlinedButton(onClick = vm::notNow, Modifier.fillMaxWidth()) { Text(stringResource(R.string.focus_not_now)) }
    }
}
