package com.aiyu.rewire.feature.profile

import android.os.Build
import androidx.compose.ui.res.stringResource
import com.aiyu.rewire.R
import androidx.compose.runtime.getValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SystemUpdate
import com.aiyu.rewire.core.update.UpdateState
import com.aiyu.rewire.feature.update.UpdateViewModel
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.rewire.BuildConfig
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.aiyu.rewire.ui.SettingsViewModel
import com.aiyu.rewire.core.settings.ThemeMode
import com.aiyu.rewire.core.settings.UserProfile
import com.aiyu.rewire.core.permissions.PermissionsPanel
import com.aiyu.rewire.feature.onboarding.AVATAR_KEY
import com.aiyu.rewire.ui.components.UserAvatar
import com.aiyu.rewire.ui.components.sharedBoundsOrSelf
import com.aiyu.rewire.core.notifications.PermissionStatus
import com.aiyu.rewire.core.notifications.rememberNotificationPermission
import com.aiyu.rewire.ui.components.SectionTitle

@Composable
fun ProfileScreen(onOpenNotificationSettings: () -> Unit, onOpenWarningLibrary: () -> Unit, onEditProfile: () -> Unit, onOpenAbout: () -> Unit, onOpenUpdates: () -> Unit) {
    val vm = hiltViewModel<SettingsViewModel>()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val warnings by hiltViewModel<WarningLibraryViewModel>().warnings.collectAsStateWithLifecycle()
    val updates = hiltViewModel<UpdateViewModel>()
    val updateState by updates.state.collectAsStateWithLifecycle()
    val s = settings ?: return
    val permission = rememberNotificationPermission()

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        UserCard(s.profile, onEditProfile)

        SectionTitle(stringResource(R.string.profile_appearance))
        Group {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Palette, contentDescription = null)
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.profile_theme), style = MaterialTheme.typography.titleMedium)
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    ThemeMode.entries.forEachIndexed { i, mode ->
                        SegmentedButton(
                            selected = s.themeMode == mode,
                            onClick = { vm.setThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                        ) { Text(stringResource(when (mode) { ThemeMode.SYSTEM -> R.string.profile_theme_system; ThemeMode.LIGHT -> R.string.profile_theme_light; ThemeMode.DARK -> R.string.profile_theme_dark })) }
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.profile_dynamic_color)) },
                    supportingContent = { Text(stringResource(R.string.profile_dynamic_color_desc)) },
                    trailingContent = { Switch(s.dynamicColor, vm::setDynamicColor) },
                    colors = groupItemColors(),
                )
            }
        }

        SectionTitle(stringResource(R.string.app_name))
        Group {
            NavRow(Icons.Rounded.Notifications, stringResource(R.string.profile_notifications), when (permission.status) {
                PermissionStatus.GRANTED -> stringResource(R.string.profile_notifications_on, s.notifications.count { it.value }, s.notifications.size)
                else -> stringResource(R.string.profile_notifications_off)
            }, onOpenNotificationSettings)
            NavRow(Icons.Rounded.FormatQuote, stringResource(R.string.profile_warning_library), stringResource(R.string.profile_warning_library_summary, warnings.count { it.enabled }, warnings.count { it.custom }), onOpenWarningLibrary)
            NavRow(
                Icons.Rounded.SystemUpdate, stringResource(R.string.profile_updates),
                when {
                    updateState is UpdateState.Available || updateState is UpdateState.Ready -> stringResource(R.string.profile_update_available)
                    !s.updatesEnabled -> stringResource(R.string.profile_update_checks_off, BuildConfig.VERSION_NAME)
                    else -> stringResource(R.string.profile_update_channel, BuildConfig.VERSION_NAME, (s.updateChannel ?: updates.channel).label)
                },
                onOpenUpdates,
            )
            NavRow(Icons.Rounded.Info, stringResource(R.string.profile_about), stringResource(R.string.profile_about_summary, BuildConfig.VERSION_NAME), onOpenAbout)
        }

        SectionTitle(stringResource(R.string.profile_permissions))
        Group { PermissionsPanel() }

        Text(
            stringResource(R.string.profile_footer, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 24.dp, start = 4.dp),
        )
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column { content() }
    }
}

@Composable
private fun groupItemColors() = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
        colors = groupItemColors(),
        modifier = Modifier.clickable(onClick = onClick),
    )
}


/** The user, not the app: avatar, name, goal and their own reason. Tap to edit. */
@Composable
private fun UserCard(p: UserProfile, onEdit: () -> Unit) {
    Card(
        onClick = onEdit,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(p.name, p.avatarShape, 72.dp, Modifier.size(72.dp).sharedBoundsOrSelf(AVATAR_KEY))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.displayName, style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(p.goal?.label ?: R.string.profile_set_goal), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                }
                Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.profile_edit))
            }
            if (p.reason.isNotBlank()) {
                Text(stringResource(R.string.profile_reason_quote, p.reason), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}
