package com.aiyu.rewire.core.permissions

import android.app.AppOpsManager
import androidx.annotation.StringRes
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.rounded.Layers
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.aiyu.rewire.BuildConfig
import com.aiyu.rewire.R
import com.aiyu.rewire.core.notifications.PermissionStatus
import com.aiyu.rewire.core.notifications.rememberNotificationPermission
import com.aiyu.rewire.service.accessibility.RewireAccessibilityService

/** Special-access permissions: granted by the user in system settings, never requested silently. */
object SystemPermissions {

    /** Rows in [PermissionsPanel]; Lite has no Accessibility row. */
    val permissionCount = if (BuildConfig.ACCESSIBILITY) 6 else 5

    fun accessibilityEnabled(context: Context): Boolean {
        if (!BuildConfig.ACCESSIBILITY) return false
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(context, RewireAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun usageAccessGranted(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun batteryUnrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    fun systemAlertWindowGranted(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    /** Guard can run without Accessibility: usage events see the app, the overlay grant lets the guard screen start from the background. */
    fun usageFallbackReady(context: Context): Boolean =
        usageAccessGranted(context) && systemAlertWindowGranted(context)

    fun systemAlertWindowSettings(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

    fun appDetailsSettings(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    fun accessibilitySettings() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    /** Most devices jump straight to Rewire's toggle with the package uri; fallback is the full list. */
    fun usageAccessSettings(context: Context) =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}"))

    fun dndAccessGranted(context: Context): Boolean =
        context.getSystemService(android.app.NotificationManager::class.java).isNotificationPolicyAccessGranted

    fun dndSettings(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    fun batterySettings() = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    fun open(context: Context, intent: Intent, fallback: Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .recoverCatching { context.startActivity(Intent(intent.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .recoverCatching { context.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

private data class PermissionRow(
    val icon: ImageVector,
    @StringRes val title: Int,
    @StringRes val why: Int,
    val granted: Boolean,
    val onAllow: () -> Unit,
)

/**
 * Every permission Rewire uses, why, live status (re-checked on resume) and an Allow action.
 * Used by onboarding and Profile so both stay identical.
 */
@Composable
fun PermissionsPanel(containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow) {
    val context = LocalContext.current
    val notifications = rememberNotificationPermission()
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { refresh++; onPauseOrDispose { } }
    var disclosure by remember { mutableStateOf(false) }

    val rows = remember(refresh, notifications.status) {
        listOfNotNull(
            PermissionRow(
                Icons.Rounded.Notifications, R.string.profile_notifications,
                R.string.perm_notifications_why,
                notifications.status == PermissionStatus.GRANTED,
                if (notifications.status == PermissionStatus.ASKABLE) notifications.request else notifications.openSettings,
            ),
            if (BuildConfig.ACCESSIBILITY) PermissionRow(
                Icons.Rounded.Accessibility, R.string.guard_accessibility,
                R.string.perm_accessibility_why,
                SystemPermissions.accessibilityEnabled(context),
            ) { disclosure = true } else null,
            PermissionRow(
                Icons.Rounded.QueryStats, R.string.perm_usage_access,
                if (BuildConfig.ACCESSIBILITY) R.string.perm_usage_why_full else R.string.perm_usage_why_lite,
                SystemPermissions.usageAccessGranted(context),
            ) { SystemPermissions.open(context, SystemPermissions.usageAccessSettings(context)) },
            PermissionRow(
                Icons.Rounded.Layers, R.string.perm_overlay,
                R.string.perm_overlay_why,
                SystemPermissions.systemAlertWindowGranted(context),
            ) { SystemPermissions.open(context, SystemPermissions.systemAlertWindowSettings(context)) },
            PermissionRow(
                Icons.Rounded.DoNotDisturbOn, R.string.perm_dnd,
                R.string.perm_dnd_why,
                SystemPermissions.dndAccessGranted(context),
            ) { SystemPermissions.open(context, SystemPermissions.dndSettings()) },
            PermissionRow(
                Icons.Rounded.BatteryChargingFull, R.string.perm_battery,
                R.string.perm_battery_why,
                SystemPermissions.batteryUnrestricted(context),
            ) { SystemPermissions.open(context, SystemPermissions.batterySettings()) },
        )
    }

    Column {
        if (BuildConfig.ACCESSIBILITY && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !SystemPermissions.accessibilityEnabled(context)) RestrictedSettingsCard()
        rows.forEach { row ->
            ListItem(
                headlineContent = { Text(stringResource(row.title)) },
                supportingContent = { Text(stringResource(row.why)) },
                leadingContent = { Icon(row.icon, contentDescription = null) },
                trailingContent = {
                    AnimatedContent(
                        targetState = row.granted,
                        transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.6f)).togetherWith(fadeOut()) },
                        label = "granted",
                    ) { granted ->
                        if (granted) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.common_allowed), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            FilledTonalButton(onClick = row.onAllow) { Text(stringResource(R.string.common_allow)) }
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = containerColor),
            )
        }
    }

    // Prominent disclosure before sending the user to Accessibility settings (Play policy + honesty).
    if (disclosure) {
        val isAndroid13Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        AlertDialog(
            onDismissRequest = { disclosure = false },
            icon = { Icon(Icons.Rounded.Accessibility, contentDescription = null) },
            title = { Text(stringResource(R.string.perm_disclosure_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.accessibility_service_description))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.perm_disclosure_steps),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (isAndroid13Plus) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.perm_disclosure_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { disclosure = false; SystemPermissions.open(context, SystemPermissions.accessibilitySettings()) }) { Text(stringResource(R.string.focus_open_settings)) }
            },
            dismissButton = {
                Row {
                    if (isAndroid13Plus) {
                        TextButton(onClick = { disclosure = false; SystemPermissions.open(context, SystemPermissions.appDetailsSettings(context)) }) {
                            Text(stringResource(R.string.guard_open_app_info))
                        }
                    }
                    TextButton(onClick = { disclosure = false }) { Text(stringResource(R.string.focus_not_now)) }
                }
            },
        )
    }
}

/** Android 13+ greys out Accessibility for sideloaded apps until "Allow restricted settings" is ticked, so it comes first. */
@Composable
private fun RestrictedSettingsCard() {
    val context = LocalContext.current
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.perm_restricted_title), style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.perm_restricted_steps),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(
                onClick = { SystemPermissions.open(context, SystemPermissions.appDetailsSettings(context)) },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.guard_open_app_info))
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Count for onboarding progress copy. */
@Composable
fun rememberGrantedCount(): Int {
    val context = LocalContext.current
    val notifications = rememberNotificationPermission()
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { refresh++; onPauseOrDispose { } }
    return remember(refresh, notifications.status) {
        listOf(
            notifications.status == PermissionStatus.GRANTED,
            SystemPermissions.accessibilityEnabled(context), // always false in Lite, which has one row fewer
            SystemPermissions.usageAccessGranted(context),
            SystemPermissions.systemAlertWindowGranted(context),
            SystemPermissions.dndAccessGranted(context),
            SystemPermissions.batteryUnrestricted(context),
        ).count { it }
    }
}
