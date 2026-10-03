package com.aiyu.rewire.feature.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.res.stringResource
import com.aiyu.rewire.R
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.aiyu.rewire.ui.SettingsViewModel
import com.aiyu.rewire.core.settings.UserGoal
import com.aiyu.rewire.core.settings.UserProfile
import com.aiyu.rewire.core.permissions.PermissionsPanel
import com.aiyu.rewire.core.permissions.SystemPermissions
import com.aiyu.rewire.core.permissions.rememberGrantedCount
import com.aiyu.rewire.domain.habit.Habit
import com.aiyu.rewire.domain.habit.HabitProfile
import com.aiyu.rewire.domain.habit.RestrictionRule
import com.aiyu.rewire.domain.habit.WarningLevel
import com.aiyu.rewire.domain.warning.Warning
import com.aiyu.rewire.domain.warning.WarningCategory
import com.aiyu.rewire.feature.guard.WarningScreen
import com.aiyu.rewire.feature.landing.HERO_KEY
import com.aiyu.rewire.ui.components.AvatarShapes
import com.aiyu.rewire.ui.components.InnerScreen
import com.aiyu.rewire.ui.components.MorphingShape
import com.aiyu.rewire.ui.components.UserAvatar
import com.aiyu.rewire.ui.components.heroBrush
import com.aiyu.rewire.ui.components.sharedBoundsOrSelf

const val AVATAR_KEY = "user-avatar"

/**
 * Who you are + why. Used as onboarding step 1 and as "Edit profile".
 * Everything optional except intent: blank name shows as "You".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileSetupScreen(onboarding: Boolean, onDone: () -> Unit, onBack: (() -> Unit)? = null) {
    val vm = hiltViewModel<SettingsViewModel>()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val initial = settings?.profile ?: return
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var goal by rememberSaveable { mutableStateOf(initial.goal) }
    var reason by rememberSaveable { mutableStateOf(initial.reason) }
    var shape by rememberSaveable { mutableIntStateOf(initial.avatarShape) }

    InnerScreen(
        title = stringResource(if (onboarding) R.string.onboarding_make_yours else R.string.profile_edit),
        subtitle = stringResource(if (onboarding) R.string.onboarding_step1_subtitle else R.string.onboarding_stays_on_device),
        onBack = onBack,
    ) {
        // Onboarding: the landing hero lands here and becomes your avatar. Edit: avatar flies in from Profile.
        UserAvatar(name, shape, 128.dp, Modifier.size(128.dp).sharedBoundsOrSelf(if (onboarding) HERO_KEY else AVATAR_KEY))

        Column(Modifier.widthIn(max = 520.dp).padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(30) },
                label = { Text(stringResource(R.string.onboarding_your_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Column {
                Text(stringResource(R.string.onboarding_avatar), style = MaterialTheme.typography.titleSmall)
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AvatarShapes.forEachIndexed { i, polygon ->
                        val selected = i == shape
                        val shapeDesc = stringResource(R.string.onboarding_avatar_shape, i + 1)
                        Surface(
                            onClick = { shape = i },
                            shape = polygon.toShape(),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                            border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primaryContainer) else null,
                            modifier = Modifier.size(48.dp).semantics { role = Role.RadioButton; this.selected = selected; contentDescription = shapeDesc },
                        ) {}
                    }
                }
            }
            Column {
                Text(stringResource(R.string.onboarding_main_goal), style = MaterialTheme.typography.titleSmall)
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UserGoal.entries.forEach { g ->
                        FilterChip(selected = goal == g, onClick = { goal = if (goal == g) null else g }, label = { Text(g.label) })
                    }
                }
            }
            OutlinedTextField(
                value = reason, onValueChange = { reason = it.take(80) },
                label = { Text(stringResource(R.string.onboarding_why)) },
                placeholder = { Text(stringResource(R.string.onboarding_reason_hint)) },
                supportingText = { Text(stringResource(R.string.onboarding_why_support)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    vm.setProfile(UserProfile(name, goal, reason, shape), then = onDone)
                },
                modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
                contentPadding = ButtonDefaults.MediumContentPadding,
            ) {
                Text(stringResource(if (onboarding) R.string.warning_continue else R.string.common_save), style = MaterialTheme.typography.titleMedium)
                if (onboarding) { Spacer(Modifier.size(8.dp)); Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null) }
            }
        }
    }
}

/** Onboarding step 2: explain + grant every permission in one place. Nothing is mandatory. */
@Composable
fun PermissionsSetupScreen(onFinish: () -> Unit, onBack: () -> Unit) {
    val granted = rememberGrantedCount()
    val totalPermissions = SystemPermissions.permissionCount
    var testProtection by remember { mutableStateOf(false) }
    var testedProtection by remember { mutableStateOf(false) }

    InnerScreen(title = stringResource(R.string.onboarding_let_help), subtitle = stringResource(R.string.guard_sheet_step, 2, 2), onBack = onBack) {
        MorphingShape(brush = heroBrush(), modifier = Modifier.size(88.dp).sharedBoundsOrSelf(HERO_KEY), rotationMillis = 30_000)
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.onboarding_permissions_intro),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 480.dp),
        )
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.widthIn(max = 560.dp).padding(top = 24.dp),
        ) { PermissionsPanel() }
        Text(
            stringResource(R.string.onboarding_granted_count, granted, totalPermissions),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 12.dp),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = { testProtection = true },
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
            contentPadding = ButtonDefaults.MediumContentPadding,
        ) {
            Icon(
                if (testedProtection) Icons.Rounded.CheckCircle else Icons.Rounded.Shield,
                contentDescription = null,
                tint = if (testedProtection) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.size(8.dp))
            Text(stringResource(if (testedProtection) R.string.onboarding_test_again else R.string.onboarding_test))
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.widthIn(max = 520.dp)) {
            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
                contentPadding = ButtonDefaults.MediumContentPadding,
            ) {
                Text(stringResource(if (granted == totalPermissions) R.string.onboarding_start else R.string.onboarding_continue_now), style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    if (testProtection) {
        Dialog(
            onDismissRequest = { testProtection = false; testedProtection = true },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            val settings by hiltViewModel<SettingsViewModel>().settings.collectAsStateWithLifecycle()
            val userReason = settings?.profile?.reason?.takeIf { it.isNotBlank() }
            val testTitle = stringResource(R.string.onboarding_test_title)
            val testMessage = stringResource(R.string.onboarding_test_message)
            val testDefaultReason = stringResource(R.string.onboarding_reason_hint)
            val sampleApp = stringResource(R.string.onboarding_sample_app)
            val testProfile = remember {
                HabitProfile(
                    habit = Habit("test-onboarding", "Doom Scrolling", null, enabled = true),
                    apps = emptyList(),
                    rule = RestrictionRule("r-test", "test-onboarding", null, null, null, null, WarningLevel.MAJOR, 5),
                )
            }
            val testWarning = remember(userReason) {
                Warning(
                    id = "test-w",
                    category = WarningCategory.CUSTOM,
                    level = WarningLevel.MAJOR,
                    title = testTitle,
                    message = testMessage,
                    motivationalMessage = userReason ?: testDefaultReason,
                    custom = false,
                )
            }
            WarningScreen(
                profile = testProfile,
                warning = testWarning,
                packageName = null,
                appLabel = sampleApp,
                preview = true,
                userReason = userReason,
                onGoBack = { testProtection = false; testedProtection = true },
                onContinue = { testProtection = false; testedProtection = true },
            )
        }
    }
}
