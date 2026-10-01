package com.rewire.app.ui

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.rewire.app.RewireApp
import com.rewire.app.core.notifications.DeepLink
import com.rewire.app.feature.focus.FocusFullscreenScreen
import com.rewire.app.feature.guard.HabitDetailScreen
import com.rewire.app.feature.onboarding.PermissionsSetupScreen
import com.rewire.app.feature.onboarding.ProfileSetupScreen
import com.rewire.app.feature.guard.WarningPreviewScreen
import com.rewire.app.feature.landing.LandingScreen
import com.rewire.app.feature.profile.NotificationSettingsScreen
import com.rewire.app.feature.profile.WarningLibraryScreen
import com.rewire.app.ui.components.LocalNavAnimatedScope
import com.rewire.app.ui.components.LocalSharedTransitionScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import androidx.compose.ui.platform.LocalContext

object Routes {
    @Serializable data object Landing
    @Serializable data object Main
    @Serializable data class HabitDetail(val id: String)
    @Serializable data class WarningPreview(val habitId: String)
    @Serializable data object NotificationSettings
    @Serializable data object WarningLibrary
    @Serializable data object FocusFullscreen
    @Serializable data object OnboardingProfile
    @Serializable data object OnboardingPermissions
    @Serializable data object EditProfile
}

@Composable
fun RewireNavHost(
    onboardingDone: Boolean,
    deepLink: DeepLink?,
    onDeepLinkConsumed: () -> Unit,
    nav: NavHostController = rememberNavController(),
) {
    val container = (LocalContext.current.applicationContext as RewireApp).container
    val scope = rememberCoroutineScope()
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
                                scope.launch { container.settingsRepository.setOnboardingDone() }
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
                        if (targetState.destination.hasRoute(Routes.HabitDetail::class) || targetState.destination.hasRoute(Routes.FocusFullscreen::class) || targetState.destination.hasRoute(Routes.EditProfile::class)) fadeOut(effects)
                        else slideOutHorizontally(spatial) { -it / 8 } + fadeOut(effects)
                    },
                    popEnterTransition = {
                        if (initialState.destination.hasRoute(Routes.HabitDetail::class) || initialState.destination.hasRoute(Routes.FocusFullscreen::class) || initialState.destination.hasRoute(Routes.EditProfile::class)) fadeIn(effects)
                        else slideInHorizontally(spatial) { -it / 8 } + fadeIn(effects)
                    },
                ) {
                    CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                        MainScreen(
                            deepLink = deepLink,
                            onDeepLinkConsumed = onDeepLinkConsumed,
                            onOpenHabit = { nav.navigate(Routes.HabitDetail(it)) },
                            onOpenNotificationSettings = { nav.navigate(Routes.NotificationSettings) },
                            onOpenWarningLibrary = { nav.navigate(Routes.WarningLibrary) },
                            onOpenFocusFullscreen = { nav.navigate(Routes.FocusFullscreen) { launchSingleTop = true } },
                            onEditProfile = { nav.navigate(Routes.EditProfile) },
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
                composable<Routes.NotificationSettings> {
                    NotificationSettingsScreen(onBack = { nav.popBackStack() })
                }
                composable<Routes.WarningLibrary> {
                    WarningLibraryScreen(onBack = { nav.popBackStack() })
                }
            }
        }
    }
}
