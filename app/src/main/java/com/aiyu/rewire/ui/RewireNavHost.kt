package com.aiyu.rewire.ui

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.aiyu.rewire.core.notifications.DeepLink
import com.aiyu.rewire.feature.focus.FocusFullscreenScreen
import com.aiyu.rewire.feature.focus.FocusHistoryScreen
import com.aiyu.rewire.feature.quit.QuitDetailScreen
import com.aiyu.rewire.BuildConfig
import com.aiyu.rewire.feature.update.UpdateScreen
import com.aiyu.rewire.feature.guard.HabitDetailScreen
import com.aiyu.rewire.feature.onboarding.PermissionsSetupScreen
import com.aiyu.rewire.feature.onboarding.ProfileSetupScreen
import com.aiyu.rewire.feature.guard.WarningPreviewScreen
import com.aiyu.rewire.feature.landing.LandingScreen
import com.aiyu.rewire.feature.matrix.MatrixBreakdownScreen
import com.aiyu.rewire.feature.profile.AboutScreen
import com.aiyu.rewire.feature.profile.AcknowledgementsScreen
import com.aiyu.rewire.feature.profile.GoalsScreen
import com.aiyu.rewire.feature.profile.LanguageScreen
import com.aiyu.rewire.feature.profile.NotificationSettingsScreen
import com.aiyu.rewire.feature.profile.WarningLibraryScreen
import com.aiyu.rewire.ui.components.LocalNavAnimatedScope
import com.aiyu.rewire.ui.theme.Accent
import com.aiyu.rewire.ui.theme.AccentTheme
import com.aiyu.rewire.ui.components.LocalSharedTransitionScope
import kotlinx.serialization.Serializable

object Routes {
    @Serializable data object Landing
    @Serializable data object Main
    @Serializable data class HabitDetail(val id: String)
    @Serializable data class QuitDetail(val id: String)
    @Serializable data class WarningPreview(val habitId: String)
    @Serializable data object NotificationSettings
    @Serializable data object WarningLibrary
    @Serializable data object FocusFullscreen
    @Serializable data object FocusHistory
    @Serializable data object OnboardingProfile
    @Serializable data object OnboardingPermissions
    @Serializable data object EditProfile
    @Serializable data object About
    @Serializable data object Updates
    @Serializable data object Acknowledgements
    @Serializable data object Language
    @Serializable data object Goals
    @Serializable data class MatrixBreakdown(val apps: Boolean)
}

@Composable
fun RewireNavHost(
    onboardingDone: Boolean,
    deepLink: DeepLink?,
    onDeepLinkConsumed: () -> Unit,
    nav: NavHostController = rememberNavController(),
) {
    val settingsVm = hiltViewModel<SettingsViewModel>()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    // Notification tap while deep inside: return to Main; MainScreen picks the tab.
    LaunchedEffect(deepLink) {
        if (deepLink != null && nav.currentDestination?.hasRoute(Routes.Main::class) == false && onboardingDone) {
            nav.popBackStack(Routes.Main, inclusive = false)
        }
    }

    // Decided once: flipping onboardingDone mid-flight must not rebuild the graph (would kill the hero transition).
    val startDestination: Any = remember { if (onboardingDone) Routes.Main else Routes.Landing }

    Box(Modifier.fillMaxSize()) {
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(
                navController = nav,
                startDestination = startDestination,
                // Forward: slide in from end; back: from start (navigation-direction rule).
                enterTransition = { slideInHorizontally(spatial) { it / 4 } + fadeIn(effects) },
                exitTransition = { slideOutHorizontally(spatial) { -it / 8 } + fadeOut(effects) },
                popEnterTransition = { slideInHorizontally(spatial) { -it / 8 } + fadeIn(effects) },
                popExitTransition = { slideOutHorizontally(spatial) { it / 4 } + fadeOut(effects) },
            ) {
                composable<Routes.Landing>(
                    exitTransition = { fadeOut(tween(450)) + scaleOut(tween(450), targetScale = 1.04f) },
                ) {
                    CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                        LandingScreen(onGetStarted = { nav.navigate(Routes.OnboardingProfile) })
                    }
                }
                // Onboarding: landing -> profile -> permissions -> app. The hero shape is shared through every step.
                composable<Routes.OnboardingProfile> {
                    CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                        ProfileSetupScreen(onboarding = true, onDone = { nav.navigate(Routes.OnboardingPermissions) }, onBack = { nav.popBackStack() })
                    }
                }
                composable<Routes.OnboardingPermissions> {
                    CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                        PermissionsSetupScreen(
                            onBack = { nav.popBackStack() },
                            onFinish = {
                                settingsVm.setOnboardingDone()
                                nav.navigate(Routes.Main) { popUpTo(Routes.Landing) { inclusive = true } }
                            },
                        )
                    }
                }
                composable<Routes.EditProfile>(
                    enterTransition = { fadeIn(effects) },
                    popExitTransition = { fadeOut(effects) },
                ) {
                    CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                        ProfileSetupScreen(onboarding = false, onDone = { nav.popBackStack() }, onBack = { nav.popBackStack() })
                    }
                }
                composable<Routes.Main>(
                    enterTransition = { fadeIn(tween(500, delayMillis = 120)) + scaleIn(tween(500, delayMillis = 120), initialScale = 0.96f) },
                    // Card -> detail is a container transform: the shared card carries motion, screens only fade.
                    exitTransition = {
                        if (targetState.destination.hasRoute(Routes.HabitDetail::class) || targetState.destination.hasRoute(Routes.QuitDetail::class) || targetState.destination.hasRoute(Routes.FocusFullscreen::class) || targetState.destination.hasRoute(Routes.EditProfile::class)) fadeOut(effects)
                        else slideOutHorizontally(spatial) { -it / 8 } + fadeOut(effects)
                    },
                    popEnterTransition = {
                        if (initialState.destination.hasRoute(Routes.HabitDetail::class) || initialState.destination.hasRoute(Routes.QuitDetail::class) || initialState.destination.hasRoute(Routes.FocusFullscreen::class) || initialState.destination.hasRoute(Routes.EditProfile::class)) fadeIn(effects)
                        else slideInHorizontally(spatial) { -it / 8 } + fadeIn(effects)
                    },
                ) {
                    CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                        MainScreen(
                            deepLink = deepLink,
                            onDeepLinkConsumed = onDeepLinkConsumed,
                            onOpenHabit = { nav.navigate(Routes.HabitDetail(it)) },
                            onOpenQuit = { nav.navigate(Routes.QuitDetail(it)) { launchSingleTop = true } },
                            onOpenNotificationSettings = { nav.navigate(Routes.NotificationSettings) },
                            onOpenWarningLibrary = { nav.navigate(Routes.WarningLibrary) },
                            onOpenMatrixBreakdown = { nav.navigate(Routes.MatrixBreakdown(it)) },
                            onOpenFocusFullscreen = { nav.navigate(Routes.FocusFullscreen) { launchSingleTop = true } },
                            onOpenFocusHistory = { nav.navigate(Routes.FocusHistory) { launchSingleTop = true } },
                            onEditProfile = { nav.navigate(Routes.EditProfile) },
                            onOpenAbout = { nav.navigate(Routes.About) },
                            onOpenUpdates = { nav.navigate(Routes.Updates) },
                            onOpenLanguage = { nav.navigate(Routes.Language) },
                            onOpenGoals = { nav.navigate(Routes.Goals) },
                            onPreviewWarning = { nav.navigate(Routes.WarningPreview(it)) },
                        )
                    }
                }
                composable<Routes.HabitDetail>(
                    enterTransition = { fadeIn(effects) },
                    popExitTransition = { fadeOut(effects) },
                ) { entry ->
                    CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                        HabitDetailScreen(
                            habitId = entry.toRoute<Routes.HabitDetail>().id,
                            onBack = { nav.popBackStack() },
                            onPreview = { nav.navigate(Routes.WarningPreview(it)) },
                        )
                    }
                }
                // Same container transform as habits: the journey card grows into this screen.
                composable<Routes.QuitDetail>(
                    enterTransition = { fadeIn(effects) },
                    popExitTransition = { fadeOut(effects) },
                ) { entry ->
                    CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                        AccentTheme(Accent.QUIT) { QuitDetailScreen(id = entry.toRoute<Routes.QuitDetail>().id, onBack = { nav.popBackStack() }) }
                    }
                }
                composable<Routes.WarningPreview>(
                    enterTransition = { fadeIn(effects) + scaleIn(initialScale = 0.9f) },
                    popExitTransition = { fadeOut(effects) + scaleOut(targetScale = 0.9f) },
                ) { entry ->
                    WarningPreviewScreen(habitId = entry.toRoute<Routes.WarningPreview>().habitId, onClose = { nav.popBackStack() })
                }
                // Timer digits are a shared element: they fly from the ring into the black screen.
                composable<Routes.FocusFullscreen>(
                    enterTransition = { fadeIn(effects) },
                    popExitTransition = { fadeOut(effects) },
                ) {
                    CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                        FocusFullscreenScreen(onExit = { nav.popBackStack() })
                    }
                }
                composable<Routes.FocusHistory> {
                    AccentTheme(Accent.FOCUS) { Surface(Modifier.fillMaxSize()) { FocusHistoryScreen(onBack = { nav.popBackStack() }) } }
                }
                composable<Routes.NotificationSettings> {
                    NotificationSettingsScreen(onBack = { nav.popBackStack() })
                }
                if (BuildConfig.UPDATES) composable<Routes.Updates> {
                    UpdateScreen(onBack = { nav.popBackStack() })
                }
                composable<Routes.About> {
                    AboutScreen(onBack = { nav.popBackStack() }, onOpenAcknowledgements = { nav.navigate(Routes.Acknowledgements) })
                }
                composable<Routes.Acknowledgements> {
                    AcknowledgementsScreen(onBack = { nav.popBackStack() })
                }
                composable<Routes.MatrixBreakdown> {
                    AccentTheme(Accent.MATRIX) { MatrixBreakdownScreen(it.toRoute<Routes.MatrixBreakdown>().apps, onBack = { nav.popBackStack() }) }
                }
                composable<Routes.Goals> {
                    GoalsScreen(onBack = { nav.popBackStack() })
                }
                composable<Routes.Language> {
                    LanguageScreen(onBack = { nav.popBackStack() })
                }
                composable<Routes.WarningLibrary> {
                    WarningLibraryScreen(onBack = { nav.popBackStack() })
                }
            }
        }
    }
    // One host for the whole app, so a change made on a detail screen can still be undone after it closes.
    val onMain = nav.currentBackStackEntryAsState().value?.destination?.hasRoute(Routes.Main::class) == true
    UndoSnackbarHost(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (onMain) 88.dp else 0.dp))
    }
}
