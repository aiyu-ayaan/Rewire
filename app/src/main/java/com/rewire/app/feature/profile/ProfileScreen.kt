package com.rewire.app.feature.profile

import android.os.Build
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
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.QueryStats
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewire.app.BuildConfig
import com.rewire.app.R
import com.rewire.app.RewireApp
import com.rewire.app.core.datastore.ThemeMode
import com.rewire.app.core.datastore.UserProfile
import com.rewire.app.core.permissions.PermissionsPanel
import com.rewire.app.feature.onboarding.AVATAR_KEY
import com.rewire.app.ui.components.UserAvatar
import com.rewire.app.ui.components.sharedBoundsOrSelf
import com.rewire.app.core.notifications.PermissionStatus
import com.rewire.app.core.notifications.rememberNotificationPermission
import com.rewire.app.ui.components.MorphingShape
import com.rewire.app.ui.components.heroBrush
import com.rewire.app.ui.components.SectionTitle
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(onOpenNotificationSettings: () -> Unit, onOpenWarningLibrary: () -> Unit, onEditProfile: () -> Unit, onOpenAbout: () -> Unit) {
    val container = (LocalContext.current.applicationContext as RewireApp).container
    val settings by container.settings.collectAsStateWithLifecycle()
    val warnings by container.warnings.warnings.collectAsStateWithLifecycle()
    val s = settings ?: return
    val scope = rememberCoroutineScope()
    val permission = rememberNotificationPermission()

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        UserCard(s.profile, onEditProfile)

        SectionTitle("Appearance")
        Group {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Palette, contentDescription = null)
                    Spacer(Modifier.width(16.dp))
                    Text("Theme", style = MaterialTheme.typography.titleMedium)
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    ThemeMode.entries.forEachIndexed { i, mode ->
                        SegmentedButton(
                            selected = s.themeMode == mode,
                            onClick = { scope.launch { container.settingsRepository.setThemeMode(mode) } },
                            shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                        ) { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ListItem(
                    headlineContent = { Text("Dynamic color") },
                    supportingContent = { Text("Match your wallpaper instead of Rewire teal.") },
                    trailingContent = { Switch(s.dynamicColor, { on -> scope.launch { container.settingsRepository.setDynamicColor(on) } }) },
                    colors = groupItemColors(),
                )
            }
        }

        SectionTitle("Rewire")
        Group {
            NavRow(Icons.Rounded.Notifications, "Notifications", when (permission.status) {
                PermissionStatus.GRANTED -> "On · ${s.notifications.count { it.value }} of ${s.notifications.size} categories"
                else -> "Off — tap to fix"
            }, onOpenNotificationSettings)
            NavRow(Icons.Rounded.FormatQuote, "Warning library", "${warnings.count { it.enabled }} active · ${warnings.count { it.custom }} custom", onOpenWarningLibrary)
            NavRow(Icons.Rounded.Info, "About Rewire", "Version ${BuildConfig.VERSION_NAME} · developer · open-source licenses", onOpenAbout)
        }

        SectionTitle("Permissions")
        Group { PermissionsPanel() }

        Text(
            "Rewire ${BuildConfig.VERSION_NAME} · All data stays on this device.",
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
                    Text(p.goal?.label ?: "Set a goal", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                }
                Icon(Icons.Rounded.Edit, contentDescription = "Edit profile")
            }
            if (p.reason.isNotBlank()) {
                Text("\u201C${p.reason}\u201D", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}
