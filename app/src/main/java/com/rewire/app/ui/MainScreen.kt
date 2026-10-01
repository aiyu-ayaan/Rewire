package com.rewire.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
    onOpenFocusFullscreen: () -> Unit,
    onEditProfile: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.GUARD) }
    val navBar = remember { NavBarController() }
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
        CompositionLocalProvider(LocalNavBarController provides navBar) {
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
                Tab.FOCUS -> FocusScreen(onFullscreen = onOpenFocusFullscreen)
                Tab.MATRIX -> MatrixScreen()
                Tab.PROFILE -> ProfileScreen(onOpenNotificationSettings = onOpenNotificationSettings, onOpenWarningLibrary = onOpenWarningLibrary, onEditProfile = onEditProfile)
            }
        }
        }
    }
    val barSpatial = motion.defaultSpatialSpec<IntOffset>()
    val barSize = motion.defaultSpatialSpec<IntSize>()
    val barEnter = slideInVertically(barSpatial) { it } + expandVertically(barSize, expandFrom = Alignment.Top) + fadeIn(effects)
    val barExit = slideOutVertically(barSpatial) { it } + shrinkVertically(barSize, shrinkTowards = Alignment.Top) + fadeOut(fastEffects)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 600.dp) {
            Row(Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    !navBar.hidden,
                    enter = slideInHorizontally(barSpatial) { -it } + expandHorizontally(barSize) + fadeIn(effects),
                    exit = slideOutHorizontally(barSpatial) { -it } + shrinkHorizontally(barSize) + fadeOut(fastEffects),
                ) {
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
                }
                Box(Modifier.weight(1f)) { content(Modifier.fillMaxSize()) }
            }
        } else {
            Scaffold(
                contentWindowInsets = WindowInsets(0),
                bottomBar = {
                    AnimatedVisibility(!navBar.hidden, enter = barEnter, exit = barExit) {
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
                    }
                },
            ) { padding ->
                content(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding))
            }
        }
    }
}

/**
 * Bottom bar / rail show only on a tab's base screen. Any in-tab "deeper" state (running focus timer, etc.)
 * calls [HideNavigationBar] while it is on screen; leaving that state or the tab restores the bar.
 */
class NavBarController {
    private var requests by mutableIntStateOf(0)
    val hidden get() = requests > 0
    fun acquire() { requests++ }
    fun release() { requests = (requests - 1).coerceAtLeast(0) }
}

val LocalNavBarController = staticCompositionLocalOf<NavBarController?> { null }

@Composable
fun HideNavigationBar(hide: Boolean = true) {
    val controller = LocalNavBarController.current ?: return
    DisposableEffect(controller, hide) {
        if (hide) controller.acquire()
        onDispose { if (hide) controller.release() }
    }
}
