package com.aiyu.rewire.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.res.stringResource
import com.aiyu.rewire.R
import androidx.annotation.StringRes
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
import com.aiyu.rewire.core.notifications.DeepLink
import com.aiyu.rewire.feature.focus.FocusScreen
import com.aiyu.rewire.feature.guard.GuardScreen
import com.aiyu.rewire.feature.matrix.MatrixScreen
import com.aiyu.rewire.feature.profile.ProfileScreen
import com.aiyu.rewire.BuildConfig
import com.aiyu.rewire.feature.focus.FocusHistoryScreen
import com.aiyu.rewire.feature.guard.HabitDetailScreen
import com.aiyu.rewire.feature.matrix.MatrixBreakdownScreen
import com.aiyu.rewire.feature.profile.AboutScreen
import com.aiyu.rewire.feature.profile.AcknowledgementsScreen
import com.aiyu.rewire.feature.profile.GoalsScreen
import com.aiyu.rewire.feature.profile.LanguageScreen
import com.aiyu.rewire.feature.profile.NotificationSettingsScreen
import com.aiyu.rewire.feature.profile.WarningLibraryScreen
import com.aiyu.rewire.feature.update.UpdateScreen
import com.aiyu.rewire.ui.components.LocalNavAnimatedScope
import com.aiyu.rewire.ui.components.LocalSharedTransitionScope
import com.aiyu.rewire.ui.components.MorphingShape
import com.aiyu.rewire.ui.components.heroBrush
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.alpha

enum class Tab(@StringRes val label: Int, val icon: ImageVector, val selectedIcon: ImageVector, val link: DeepLink) {
    GUARD(R.string.nav_guard, Icons.Outlined.Shield, Icons.Rounded.Shield, DeepLink.GUARD),
    FOCUS(R.string.nav_focus, Icons.Outlined.Timer, Icons.Rounded.Timer, DeepLink.FOCUS),
    MATRIX(R.string.matrix_title, Icons.Outlined.Insights, Icons.Rounded.Insights, DeepLink.MATRIX),
    PROFILE(R.string.nav_profile, Icons.Outlined.Person, Icons.Rounded.Person, DeepLink.PROFILE),
}

@Composable
fun MainScreen(
    deepLink: DeepLink?,
    onDeepLinkConsumed: () -> Unit,
    onOpenHabit: (String) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenWarningLibrary: () -> Unit,
    onOpenMatrixBreakdown: (apps: Boolean) -> Unit,
    onOpenFocusFullscreen: () -> Unit,
    onOpenFocusHistory: () -> Unit,
    onEditProfile: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenGoals: () -> Unit,
    onPreviewWarning: (String) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.GUARD) }
    // On wide windows a screen opened from a tab shows beside the list instead of replacing it (see TabPane).
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    val select = { t: Tab -> tab = t; detail = null }
    val navBar = remember { NavBarController() }
    LaunchedEffect(deepLink) {
        if (deepLink != null) {
            select(Tab.entries.first { it.link == deepLink })
            onDeepLinkConsumed()
        }
    }

    val motion = MaterialTheme.motionScheme
    val effects = motion.defaultEffectsSpec<Float>()
    val spatial = motion.defaultSpatialSpec<Float>()
    val fastEffects = motion.fastEffectsSpec<Float>()
    val content: @Composable (Modifier, Boolean) -> Unit = { modifier, wide ->
        val open = { key: String, narrow: () -> Unit -> if (wide) detail = key else narrow() }
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
                Tab.GUARD -> TabPane(wide, detail, { DetailContent(it, { detail = null }, { k -> detail = k }, onPreviewWarning) }) {
                    GuardScreen(onOpenHabit = { open("habit:$it") { onOpenHabit(it) } }, onStartFocus = { select(Tab.FOCUS) })
                }
                Tab.FOCUS -> TabPane(wide, detail, { DetailContent(it, { detail = null }, { k -> detail = k }, onPreviewWarning) }) {
                    FocusScreen(onFullscreen = onOpenFocusFullscreen, onHistory = { open("history", onOpenFocusHistory) })
                }
                Tab.MATRIX -> TabPane(wide, detail, { DetailContent(it, { detail = null }, { k -> detail = k }, onPreviewWarning) }) {
                    MatrixScreen(onShowAll = { apps -> open("matrix:$apps") { onOpenMatrixBreakdown(apps) } })
                }
                Tab.PROFILE -> TabPane(wide, detail ?: if (wide) "notifications" else null, { DetailContent(it, { detail = null }, { k -> detail = k }, onPreviewWarning) }) {
                    ProfileScreen(
                        onOpenNotificationSettings = { open("notifications", onOpenNotificationSettings) },
                        onOpenWarningLibrary = { open("warnings", onOpenWarningLibrary) },
                        onEditProfile = onEditProfile,
                        onOpenAbout = { open("about", onOpenAbout) },
                        onOpenUpdates = { open("updates", onOpenUpdates) },
                        onOpenLanguage = { open("language", onOpenLanguage) },
                        onOpenGoals = { open("goals", onOpenGoals) },
                    )
                }
            }
        }
        }
    }
    val barSpatial = motion.defaultSpatialSpec<IntOffset>()
    val barSize = motion.defaultSpatialSpec<IntSize>()
    val barEnter = slideInVertically(barSpatial) { it } + expandVertically(barSize, expandFrom = Alignment.Top) + fadeIn(effects)
    val barExit = slideOutVertically(barSpatial) { it } + shrinkVertically(barSize, shrinkTowards = Alignment.Top) + fadeOut(fastEffects)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Content width left of the rail: list + detail only when both panes get a comfortable size.
        val widePane = maxWidth >= 920.dp
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
                            onClick = { select(t) },
                            icon = { Icon(if (t == tab) t.selectedIcon else t.icon, contentDescription = null) },
                            label = { Text(stringResource(t.label)) },
                        )
                    }
                }
                }
                Box(Modifier.weight(1f)) { content(Modifier.fillMaxSize(), widePane) }
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
                                onClick = { select(t) },
                                icon = { Icon(if (t == tab) t.selectedIcon else t.icon, contentDescription = null) },
                                label = { Text(stringResource(t.label)) },
                            )
                        }
                    }
                    }
                },
            ) { padding ->
                content(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), false)
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

/** Wide windows: [list] on the left (fixed width), the opened screen on the right like Android Settings. Narrow: [list] only. */
@Composable
private fun TabPane(wide: Boolean, detail: String?, detailContent: @Composable (String) -> Unit, list: @Composable () -> Unit) {
    if (!wide) { list(); return }
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.width(420.dp).fillMaxHeight()) { list() }
        Surface(
            Modifier.weight(1f).fillMaxHeight().padding(top = 8.dp, end = 8.dp, bottom = 8.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            // The pane is not a nav destination: hero/container transforms don't apply here.
            CompositionLocalProvider(LocalSharedTransitionScope provides null, LocalNavAnimatedScope provides null) {
                if (detail != null) detailContent(detail)
                else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    MorphingShape(brush = heroBrush(), modifier = Modifier.size(96.dp).alpha(0.4f))
                }
            }
        }
    }
}

/** Screens that open beside the list on wide windows. [key] is "kind" or "kind:arg" and survives rotation. */
@Composable
private fun DetailContent(key: String, onClose: () -> Unit, open: (String) -> Unit, onPreviewWarning: (String) -> Unit) {
    when {
        key.startsWith("habit:") -> HabitDetailScreen(key.removePrefix("habit:"), onBack = onClose, onPreview = onPreviewWarning)
        key.startsWith("matrix:") -> MatrixBreakdownScreen(key.removePrefix("matrix:").toBoolean(), onBack = onClose)
        key == "history" -> FocusHistoryScreen(onBack = onClose)
        key == "notifications" -> NotificationSettingsScreen(onBack = onClose)
        key == "warnings" -> WarningLibraryScreen(onBack = onClose)
        key == "about" -> AboutScreen(onBack = onClose, onOpenAcknowledgements = { open("acks") })
        key == "acks" -> AcknowledgementsScreen(onBack = { open("about") })
        key == "updates" -> if (BuildConfig.UPDATES) UpdateScreen(onBack = onClose)
        key == "goals" -> GoalsScreen(onBack = onClose)
        key == "language" -> LanguageScreen(onBack = onClose)
    }
}
