package com.rewire.app.feature.onboarding

import androidx.compose.foundation.BorderStroke
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
import com.rewire.app.ui.SettingsViewModel
import com.rewire.app.core.settings.UserGoal
import com.rewire.app.core.settings.UserProfile
import com.rewire.app.core.permissions.PermissionsPanel
import com.rewire.app.core.permissions.rememberGrantedCount
import com.rewire.app.domain.habit.Habit
import com.rewire.app.domain.habit.HabitProfile
import com.rewire.app.domain.habit.RestrictionRule
import com.rewire.app.domain.habit.WarningLevel
import com.rewire.app.domain.warning.Warning
import com.rewire.app.domain.warning.WarningCategory
import com.rewire.app.feature.guard.WarningScreen
import com.rewire.app.feature.landing.HERO_KEY
import com.rewire.app.ui.components.AvatarShapes
import com.rewire.app.ui.components.InnerScreen
import com.rewire.app.ui.components.MorphingShape
import com.rewire.app.ui.components.UserAvatar
import com.rewire.app.ui.components.heroBrush
import com.rewire.app.ui.components.sharedBoundsOrSelf

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
        title = if (onboarding) "Make it yours" else "Edit profile",
        subtitle = if (onboarding) "Step 1 of 2 · stays on this device" else "Stays on this device",
        onBack = onBack,
    ) {
        // Onboarding: the landing hero lands here and becomes your avatar. Edit: avatar flies in from Profile.
        UserAvatar(name, shape, 128.dp, Modifier.size(128.dp).sharedBoundsOrSelf(if (onboarding) HERO_KEY else AVATAR_KEY))

        Column(Modifier.widthIn(max = 520.dp).padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(30) },
                label = { Text("Your name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Column {
                Text("Avatar", style = MaterialTheme.typography.titleSmall)
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AvatarShapes.forEachIndexed { i, polygon ->
                        val selected = i == shape
                        Surface(
                            onClick = { shape = i },
                            shape = polygon.toShape(),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                            border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primaryContainer) else null,
                            modifier = Modifier.size(48.dp).semantics { role = Role.RadioButton; this.selected = selected; contentDescription = "Avatar shape ${i + 1}" },
                        ) {}
                    }
                }
            }
            Column {
                Text("Main goal", style = MaterialTheme.typography.titleSmall)
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UserGoal.entries.forEach { g ->
                        FilterChip(selected = goal == g, onClick = { goal = if (goal == g) null else g }, label = { Text(g.label) })
                    }
                }
            }
            OutlinedTextField(
                value = reason, onValueChange = { reason = it.take(80) },
                label = { Text("Why it matters to you") },
                placeholder = { Text("More focus. Less wasted time.") },
                supportingText = { Text("Rewire shows this back to you when you need it.") },
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
                Text(if (onboarding) "Continue" else "Save", style = MaterialTheme.typography.titleMedium)
                if (onboarding) { Spacer(Modifier.size(8.dp)); Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null) }
            }
        }
    }
}

/** Onboarding step 2: explain + grant every permission in one place. Nothing is mandatory. */
@Composable
fun PermissionsSetupScreen(onFinish: () -> Unit, onBack: () -> Unit) {
    val granted = rememberGrantedCount()
    val totalPermissions = 6
    var testProtection by remember { mutableStateOf(false) }
    var testedProtection by remember { mutableStateOf(false) }

    InnerScreen(title = "Let Rewire help", subtitle = "Step 2 of 2", onBack = onBack) {
        MorphingShape(brush = heroBrush(), modifier = Modifier.size(88.dp).sharedBoundsOrSelf(HERO_KEY), rotationMillis = 30_000)
        Spacer(Modifier.height(16.dp))
        Text(
            "Each one has a single job, explained below. Data never leaves your phone. Change any of them later in Profile.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 480.dp),
        )
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.widthIn(max = 560.dp).padding(top = 24.dp),
        ) { PermissionsPanel() }
        Text(
            "$granted of $totalPermissions allowed",
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
            Text(if (testedProtection) "Test protection again" else "Test protection")
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.widthIn(max = 520.dp)) {
            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
                contentPadding = ButtonDefaults.MediumContentPadding,
            ) {
                Text(if (granted == totalPermissions) "Start using Rewire" else "Continue for now", style = MaterialTheme.typography.titleMedium)
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
                    title = "Pause and reflect",
                    message = "This is what happens when you open a guarded app. Rewire creates a deliberate moment to choose.",
                    motivationalMessage = userReason ?: "More focus. Less wasted time.",
                    custom = false,
                )
            }
            WarningScreen(
                profile = testProfile,
                warning = testWarning,
                packageName = null,
                appLabel = "Sample App",
                preview = true,
                userReason = userReason,
                onGoBack = { testProtection = false; testedProtection = true },
                onContinue = { testProtection = false; testedProtection = true },
            )
        }
    }
}
