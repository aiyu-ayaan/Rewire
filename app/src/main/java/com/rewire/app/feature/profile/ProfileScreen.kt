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
import androidx.compose.material.icons.rounded.Accessibility
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
import com.rewire.app.core.notifications.PermissionStatus
import com.rewire.app.core.notifications.rememberNotificationPermission
import com.rewire.app.ui.components.MorphingShape
import com.rewire.app.ui.components.heroBrush
import com.rewire.app.ui.components.SectionTitle
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(onOpenNotificationSettings: () -> Unit, onOpenWarningLibrary: () -> Unit) {
    val container = (LocalContext.current.applicationContext as RewireApp).container
    val settings by container.settings.collectAsStateWithLifecycle()
    val warnings by container.warnings.warnings.collectAsStateWithLifecycle()
    val s = settings ?: return
    val scope = rememberCoroutineScope()
    val permission = rememberNotificationPermission()

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            MorphingShape(
                brush = heroBrush(),
                modifier = Modifier.size(64.dp),
                rotationMillis = 40_000,
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Profile", style = MaterialTheme.typography.headlineLarge)
                Text(stringResource(R.string.tagline).replace("\n", " "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

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
        }

        SectionTitle("Permissions")
        Group {
            StatusRow(Icons.Rounded.Notifications, "Notifications", if (permission.status == PermissionStatus.GRANTED) "Allowed" else "Not allowed", ok = permission.status == PermissionStatus.GRANTED)
            StatusRow(Icons.Rounded.Accessibility, "Accessibility service", "Needed to detect protected apps. Set up when Guard goes live.", ok = null)
            StatusRow(Icons.Rounded.QueryStats, "Usage access", "Powers screen-time charts. Coming with usage analytics.", ok = null)
            StatusRow(Icons.Rounded.BatteryAlert, "Battery optimization", "Guidance to keep protection running on aggressive devices.", ok = null)
        }

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

/** ok = null -> upcoming feature (neutral), never shown as an error. */
@Composable
private fun StatusRow(icon: ImageVector, title: String, subtitle: String, ok: Boolean?) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            Text(
                when (ok) { true -> "OK"; false -> "Action needed"; null -> "Later" },
                style = MaterialTheme.typography.labelLarge,
                color = when (ok) { true -> MaterialTheme.colorScheme.primary; false -> MaterialTheme.colorScheme.error; null -> MaterialTheme.colorScheme.onSurfaceVariant },
            )
        },
        colors = groupItemColors(),
    )
}
