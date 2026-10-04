package com.aiyu.rewire.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aiyu.rewire.core.notifications.DeepLink
import com.aiyu.rewire.feature.focus.FocusScreen
import com.aiyu.rewire.feature.guard.GuardScreen
import com.aiyu.rewire.feature.matrix.MatrixScreen
import com.aiyu.rewire.feature.quit.QuitDetailScreen
import com.aiyu.rewire.feature.quit.QuitScreen
import com.aiyu.rewire.feature.profile.ProfileScreen
import com.aiyu.rewire.BuildConfig
import com.aiyu.rewire.feature.guard.HabitDetailScreen
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
    QUIT(R.string.nav_quit, Icons.Outlined.Spa, Icons.Rounded.Spa, DeepLink.QUIT),
    MATRIX(R.string.matrix_title, Icons.Outlined.Insights, Icons.Rounded.Insights, DeepLink.MATRIX),
    PROFILE(R.string.nav_profile, Icons.Outlined.Person, Icons.Rounded.Person, DeepLink.PROFILE),
}

@Composable
fun MainScreen(
    deepLink: DeepLink?,
    onDeepLinkConsumed: () -> Unit,
    onOpenHabit: (String) -> Unit,
    onOpenQuit: (String) -> Unit,
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
                // Focus and Matrix use the full width: nothing beside them needs a detail pane. Guard, Quit and Profile open theirs beside the list.
                Tab.FOCUS -> FocusScreen(onFullscreen = onOpenFocusFullscreen, onHistory = onOpenFocusHistory)
                Tab.QUIT -> TabPane(wide, detail, { DetailContent(it, { detail = null }, { k -> detail = k }, onPreviewWarning) }) {
                    QuitScreen(onOpen = { open("quit:$it") { onOpenQuit(it) } })
                }
                Tab.MATRIX -> MatrixScreen(onShowAll = onOpenMatrixBreakdown)
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
                    FloatingNavRail(tab, select)
                }
                Box(Modifier.weight(1f)) { content(Modifier.fillMaxSize(), widePane) }
            }
        } else {
            // The pill floats over content (no reserved strip). Scrolling content down tucks it away, scrolling up
            // brings it back; only real scrolls count, so screens that can't scroll keep it. Screens read
            // LocalBottomBarInsets to keep their last item and FAB clear of it.
            var scrolledAway by remember { mutableStateOf(false) }
            LaunchedEffect(tab) { scrolledAway = false }
            val hideOnScroll = remember {
                object : NestedScrollConnection {
                    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                        if (consumed.y < -1f) scrolledAway = true else if (consumed.y > 1f) scrolledAway = false
                        return Offset.Zero
                    }
                }
            }
            val density = LocalDensity.current
            var pill by remember { mutableStateOf(0.dp) }
            val shown = !navBar.hidden && !scrolledAway
            val fab by animateDpAsState(if (shown) pill else 0.dp, motion.defaultSpatialSpec(), label = "fabLift")
            Box(Modifier.fillMaxSize().nestedScroll(hideOnScroll)) {
                CompositionLocalProvider(LocalBottomBarInsets provides BottomBarInsets(content = pill, fab = fab)) {
                    content(Modifier.fillMaxSize(), false)
                }
                AnimatedVisibility(
                    shown,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically(barSpatial) { it } + fadeIn(effects),
                    exit = slideOutVertically(barSpatial) { it } + fadeOut(fastEffects),
                ) {
                    FloatingNavBar(tab, select, Modifier.onSizeChanged { pill = with(density) { it.height.toDp() } })
                }
            }
        }
    }
}

/** Space the floating pill covers at the bottom: [content] for scroll padding (fixed), [fab] follows the pill as it hides. */
data class BottomBarInsets(val content: Dp = 0.dp, val fab: Dp = 0.dp)

val LocalBottomBarInsets = compositionLocalOf { BottomBarInsets() }

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
        key.startsWith("quit:") -> QuitDetailScreen(key.removePrefix("quit:"), onBack = onClose)
        key.startsWith("habit:") -> HabitDetailScreen(key.removePrefix("habit:"), onBack = onClose, onPreview = onPreviewWarning)
        key == "notifications" -> NotificationSettingsScreen(onBack = onClose)
        key == "warnings" -> WarningLibraryScreen(onBack = onClose)
        key == "about" -> AboutScreen(onBack = onClose, onOpenAcknowledgements = { open("acks") })
        key == "acks" -> AcknowledgementsScreen(onBack = { open("about") })
        key == "updates" -> if (BuildConfig.UPDATES) UpdateScreen(onBack = onClose)
        key == "goals" -> GoalsScreen(onBack = onClose)
        key == "language" -> LanguageScreen(onBack = onClose)
    }
}

/**
 * Phones: a floating pill (Google Photos style). The selected tab grows into icon + label; the
 * others stay icon-only so five tabs fit in every language at 360dp.
 */
@Composable
private fun FloatingNavBar(tab: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 6.dp,
        ) {
            Row(Modifier.padding(6.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Tab.entries.forEach { t -> PillItem(t, t == tab) { onSelect(t) } }
            }
        }
    }
}

@Composable
private fun PillItem(t: Tab, selected: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val bg by animateColorAsState(if (selected) c.secondaryContainer else Color.Transparent, effects, label = "pillBg")
    val fg by animateColorAsState(if (selected) c.onSecondaryContainer else c.onSurfaceVariant, effects, label = "pillFg")
    val label = stringResource(t.label)
    Row(
        Modifier
            .clip(CircleShape)
            .background(bg)
            .selectable(selected, onClick = onClick, role = Role.Tab)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp)
            .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (selected) t.selectedIcon else t.icon, contentDescription = null, tint = fg)
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp))
        }
    }
}

/** Tablets / landscape: the same pill stood upright on the left edge, like Google Photos on large screens. */
@Composable
private fun FloatingNavRail(tab: Tab, onSelect: (Tab) -> Unit) {
    Box(Modifier.fillMaxHeight().systemBarsPadding().displayCutoutPadding().padding(start = 12.dp, top = 12.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 6.dp,
        ) {
            Column(
                Modifier.width(88.dp).padding(vertical = 12.dp).verticalScroll(rememberScrollState()).selectableGroup(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Tab.entries.forEach { t ->
                    NavigationRailItem(
                        selected = t == tab,
                        onClick = { onSelect(t) },
                        icon = { Icon(if (t == tab) t.selectedIcon else t.icon, contentDescription = null) },
                        label = { Text(stringResource(t.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = NavigationRailItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.secondaryContainer),
                    )
                }
            }
        }
    }
}
