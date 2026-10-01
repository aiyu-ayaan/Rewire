package com.rewire.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.rewire.app.core.notifications.DeepLink
import com.rewire.app.feature.focus.FocusScreen
import com.rewire.app.feature.guard.GuardScreen
import com.rewire.app.feature.matrix.MatrixScreen
import com.rewire.app.feature.profile.ProfileScreen

enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector, val link: DeepLink) {
    GUARD("Guard", Icons.Outlined.Shield, Icons.Rounded.Shield, DeepLink.GUARD),
    FOCUS("Focus", Icons.Outlined.Timer, Icons.Rounded.Timer, DeepLink.FOCUS),
    MATRIX("Matrix", Icons.Outlined.Insights, Icons.Rounded.Insights, DeepLink.MATRIX),
    PROFILE("Profile", Icons.Outlined.Person, Icons.Rounded.Person, DeepLink.PROFILE),
}

@Composable
fun MainScreen(
    deepLink: DeepLink?,
    onDeepLinkConsumed: () -> Unit,
    onOpenHabit: (String) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenWarningLibrary: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.GUARD) }
    LaunchedEffect(deepLink) {
        if (deepLink != null) {
            tab = Tab.entries.first { it.link == deepLink }
            onDeepLinkConsumed()
        }
    }

    val motion = MaterialTheme.motionScheme
    val effects = motion.defaultEffectsSpec<Float>()
    val spatial = motion.defaultSpatialSpec<Float>()
    val fastEffects = motion.fastEffectsSpec<Float>()
    val content: @Composable (Modifier) -> Unit = { modifier ->
        AnimatedContent(
            targetState = tab,
            modifier = modifier,
            // M3 "fade through": outgoing fades fast, incoming fades + scales up slightly.
            transitionSpec = {
                (fadeIn(effects) + scaleIn(spatial, initialScale = 0.94f)).togetherWith(fadeOut(fastEffects))
            },
            label = "tab",
        ) { current ->
            when (current) {
                Tab.GUARD -> GuardScreen(onOpenHabit = onOpenHabit, onStartFocus = { tab = Tab.FOCUS })
                Tab.FOCUS -> FocusScreen()
                Tab.MATRIX -> MatrixScreen()
                Tab.PROFILE -> ProfileScreen(onOpenNotificationSettings = onOpenNotificationSettings, onOpenWarningLibrary = onOpenWarningLibrary)
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 600.dp) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Tab.entries.forEach { t ->
                        NavigationRailItem(
                            selected = t == tab,
                            onClick = { tab = t },
                            icon = { Icon(if (t == tab) t.selectedIcon else t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                        )
                    }
                }
                Box(Modifier.weight(1f)) { content(Modifier.fillMaxSize()) }
            }
        } else {
            Scaffold(
                contentWindowInsets = WindowInsets(0),
                bottomBar = {
                    ShortNavigationBar {
                        Tab.entries.forEach { t ->
                            ShortNavigationBarItem(
                                selected = t == tab,
                                onClick = { tab = t },
                                icon = { Icon(if (t == tab) t.selectedIcon else t.icon, contentDescription = null) },
                                label = { Text(t.label) },
                            )
                        }
                    }
                },
            ) { padding ->
                content(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding))
            }
        }
    }
}

