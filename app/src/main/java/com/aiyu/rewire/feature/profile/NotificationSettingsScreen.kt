package com.aiyu.rewire.feature.profile

import com.aiyu.rewire.ui.components.readableWidth

import androidx.compose.foundation.layout.Column
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.aiyu.rewire.R
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.aiyu.rewire.ui.SettingsViewModel
import com.aiyu.rewire.core.settings.NotificationCategory
import com.aiyu.rewire.core.notifications.NotificationRationaleCard
import com.aiyu.rewire.core.notifications.PermissionStatus
import com.aiyu.rewire.core.notifications.RewireNotifier.Channels
import com.aiyu.rewire.core.notifications.rememberNotificationPermission
import kotlinx.coroutines.launch

private data class CategoryInfo(val category: NotificationCategory, val icon: ImageVector, @StringRes val title: Int, @StringRes val body: Int, val channels: List<String>)

private val categories = listOf(
    CategoryInfo(NotificationCategory.FOCUS, Icons.Rounded.Timer, R.string.notif_cat_focus, R.string.notif_cat_focus_desc, listOf(Channels.FOCUS_SESSION, Channels.FOCUS_ALERTS)),
    CategoryInfo(NotificationCategory.GUARD, Icons.Rounded.Shield, R.string.nav_guard, R.string.notif_cat_guard_desc, listOf(Channels.GUARD)),
    CategoryInfo(NotificationCategory.SUMMARY, Icons.Rounded.Insights, R.string.channel_summary, R.string.notif_cat_summary_desc, listOf(Channels.SUMMARY)),
)

@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm = hiltViewModel<SettingsViewModel>()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    val permission = rememberNotificationPermission()
    val granted = permission.status == PermissionStatus.GRANTED
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val testSentText = stringResource(R.string.notif_test_sent)
    val testFailedText = stringResource(R.string.notif_test_failed)
    val silenceDesc = stringResource(R.string.notif_silence_during_focus)
    val categoryTitles = categories.associate { it.category to stringResource(it.title) }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.profile_notifications)) },
                subtitle = { Text(stringResource(R.string.notif_settings_subtitle)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.warning_back)) } },
                scrollBehavior = scroll,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).readableWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            NotificationRationaleCard(
                permission,
                reason = stringResource(R.string.notif_settings_reason),
                modifier = Modifier.padding(bottom = 16.dp),
            )
            Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                categories.forEach { info ->
                    val systemOff = granted && info.channels.all { !vm.channelEnabled(it) }
                    ListItem(
                        headlineContent = { Text(stringResource(info.title)) },
                        supportingContent = { Text(stringResource(if (systemOff) R.string.notif_off_in_system else info.body)) },
                        leadingContent = { Icon(info.icon, contentDescription = null) },
                        trailingContent = {
                            Switch(
                                checked = s.notifications[info.category] == true && !systemOff,
                                onCheckedChange = { on ->
                                    if (systemOff) context.startActivity(vm.appNotificationSettingsIntent())
                                    else vm.setNotification(info.category, on)
                                },
                                enabled = granted,
                                modifier = Modifier.semantics { contentDescription = categoryTitles.getValue(info.category) },
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                val dndGranted = vm.isDndAccessGranted
                ListItem(
                    headlineContent = { Text(stringResource(R.string.notif_silence_during_focus)) },
                    supportingContent = {
                        Text(
                            stringResource(if (!dndGranted) R.string.focus_dnd_grant else R.string.notif_silence_body)
                        )
                    },
                    leadingContent = { Icon(Icons.Rounded.DoNotDisturbOn, contentDescription = null) },
                    trailingContent = {
                        if (!dndGranted) {
                            FilledTonalButton(onClick = { context.startActivity(vm.dndSettingsIntent()) }) {
                                Text(stringResource(R.string.common_allow))
                            }
                        } else {
                            Switch(
                                checked = s.focusDndEnabled,
                                onCheckedChange = vm::setFocusDnd,
                                modifier = Modifier.semantics { contentDescription = silenceDesc },
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                )
            }
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(
                onClick = {
                    val sent = vm.sendTestNotification()
                    scope.launch { snackbar.showSnackbar(if (sent) testSentText else testFailedText) }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.notif_send_test))
            }
            TextButton(onClick = { context.startActivity(vm.appNotificationSettingsIntent()) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.notif_system_settings))
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            Text(
                stringResource(R.string.notif_system_settings_note),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
            )
        }
    }
}
