package com.rewire.app.feature.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewire.app.RewireApp
import com.rewire.app.core.settings.NotificationCategory
import com.rewire.app.core.notifications.NotificationRationaleCard
import com.rewire.app.core.notifications.PermissionStatus
import com.rewire.app.core.notifications.RewireNotifier.Channels
import com.rewire.app.core.notifications.rememberNotificationPermission
import kotlinx.coroutines.launch

private data class CategoryInfo(val category: NotificationCategory, val icon: ImageVector, val title: String, val body: String, val channels: List<String>)

private val categories = listOf(
    CategoryInfo(NotificationCategory.FOCUS, Icons.Rounded.Timer, "Focus sessions", "Silent running timer, break started, session complete.", listOf(Channels.FOCUS_SESSION, Channels.FOCUS_ALERTS)),
    CategoryInfo(NotificationCategory.GUARD, Icons.Rounded.Shield, "Guard", "Important restriction status. Never one per app open.", listOf(Channels.GUARD)),
    CategoryInfo(NotificationCategory.SUMMARY, Icons.Rounded.Insights, "Daily summary", "One short recap in the evening (arrives with analytics).", listOf(Channels.SUMMARY)),
)

@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as RewireApp).container
    val settings by container.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    val permission = rememberNotificationPermission()
    val granted = permission.status == PermissionStatus.GRANTED
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Notifications") },
                subtitle = { Text("Only what helps. Nothing else.") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                scrollBehavior = scroll,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            NotificationRationaleCard(
                permission,
                reason = "Rewire uses notifications for focus timers and important Guard status. No marketing, no streak nagging.",
                modifier = Modifier.padding(bottom = 16.dp),
            )
            Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                categories.forEach { info ->
                    val systemOff = granted && info.channels.all { !container.notifier.channelEnabled(it) }
                    ListItem(
                        headlineContent = { Text(info.title) },
                        supportingContent = { Text(if (systemOff) "Turned off in system settings" else info.body) },
                        leadingContent = { Icon(info.icon, contentDescription = null) },
                        trailingContent = {
                            Switch(
                                checked = s.notifications[info.category] == true && !systemOff,
                                onCheckedChange = { on ->
                                    if (systemOff) context.startActivity(container.notifier.appSettingsIntent())
                                    else scope.launch { container.settingsRepository.setNotification(info.category, on) }
                                },
                                enabled = granted,
                                modifier = Modifier.semantics { contentDescription = info.title },
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                val dndGranted = container.dndManager.isAccessGranted
                ListItem(
                    headlineContent = { Text("Silence during Focus") },
                    supportingContent = {
                        Text(
                            if (!dndGranted) "Tap to grant Do Not Disturb access. Incoming calls will ring; all messages silenced."
                            else "Silences messages & alerts from all apps during focus sessions. Incoming calls still ring."
                        )
                    },
                    leadingContent = { Icon(Icons.Rounded.DoNotDisturbOn, contentDescription = null) },
                    trailingContent = {
                        if (!dndGranted) {
                            FilledTonalButton(onClick = { context.startActivity(container.dndManager.dndSettingsIntent()) }) {
                                Text("Allow")
                            }
                        } else {
                            Switch(
                                checked = s.focusDndEnabled,
                                onCheckedChange = { on -> scope.launch { container.settingsRepository.setFocusDndEnabled(on); container.focus.onDndSettingChanged(on) } },
                                modifier = Modifier.semantics { contentDescription = "Silence during Focus" },
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                )
            }
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(
                onClick = {
                    val sent = container.notifier.sendTest()
                    scope.launch { snackbar.showSnackbar(if (sent) "Test sent. Check your shade." else "Can't send — notifications are off.") }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Send test notification")
            }
            TextButton(onClick = { context.startActivity(container.notifier.appSettingsIntent()) }, modifier = Modifier.fillMaxWidth()) {
                Text("System notification settings")
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            Text(
                "Sound, vibration and lock-screen detail for each category live in system settings. Focus timers stay silent.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
            )
        }
    }
}
