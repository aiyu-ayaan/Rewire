package com.rewire.app.core.notifications

import android.Manifest
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewire.app.RewireApp
import kotlinx.coroutines.launch

enum class PermissionStatus { GRANTED, ASKABLE, BLOCKED }

class NotificationPermissionState(
    val status: PermissionStatus,
    val request: () -> Unit,
    val openSettings: () -> Unit,
)

/**
 * POST_NOTIFICATIONS flow: in-app rationale first (caller UI), then the system prompt once.
 * After a denial the only recovery is system settings, so status becomes BLOCKED.
 * Re-checks on every resume (user may change it in Settings).
 */
@Composable
fun rememberNotificationPermission(): NotificationPermissionState {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val container = (context.applicationContext as RewireApp).container
    val settings by container.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { refresh++; onPauseOrDispose { } }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        scope.launch { container.settingsRepository.setNotificationPermissionAsked() }
        refresh++
    }

    val asked = settings?.notificationPermissionAsked == true
    val status = remember(refresh, asked) { when {
        container.notifier.hasPermission() -> PermissionStatus.GRANTED
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> PermissionStatus.BLOCKED // disabled in system settings
        !asked -> PermissionStatus.ASKABLE
        activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true -> PermissionStatus.ASKABLE
        else -> PermissionStatus.BLOCKED
    } }
    return NotificationPermissionState(
        status = status,
        request = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        openSettings = { context.startActivity(container.notifier.appSettingsIntent()) },
    )
}

/** Rationale card shown before asking. Explains why; never nags once granted. */
@Composable
fun NotificationRationaleCard(state: NotificationPermissionState, reason: String, modifier: Modifier = Modifier) {
    AnimatedVisibility(state.status != PermissionStatus.GRANTED, modifier = modifier) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.NotificationsActive, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (state.status == PermissionStatus.BLOCKED) "Notifications are off" else "Allow notifications?",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(reason, style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (state.status == PermissionStatus.BLOCKED) {
                        TextButton(onClick = state.openSettings) { Text("Open settings") }
                    } else {
                        Button(onClick = state.request) { Text("Allow") }
                    }
                }
            }
        }
    }
}
