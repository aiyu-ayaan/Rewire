package com.aiyu.rewire

import androidx.lifecycle.lifecycleScope
import com.aiyu.rewire.core.update.AppUpdater
import com.aiyu.rewire.core.update.PlayUpdates
import com.aiyu.rewire.feature.update.UpdateHost
import kotlinx.coroutines.launch
import com.aiyu.rewire.core.focus.FocusController
import com.aiyu.rewire.core.guard.HabitEngine
import com.aiyu.rewire.core.settings.Settings
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.animation.PathInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.rewire.core.settings.ThemeMode
import com.aiyu.rewire.core.notifications.DeepLink
import com.aiyu.rewire.core.notifications.RewireNotifier
import com.aiyu.rewire.ui.RewireNavHost
import com.aiyu.rewire.ui.theme.RewireTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsState: StateFlow<Settings?>
    @Inject lateinit var notifier: RewireNotifier
    @Inject lateinit var focus: FocusController
    @Inject lateinit var updater: dagger.Lazy<AppUpdater> // Lazy: never built when BuildConfig.UPDATES is off (play)
    @Inject lateinit var engine: HabitEngine
    @Inject lateinit var warnings: com.aiyu.rewire.data.WarningRepository

    private var deepLink by mutableStateOf<DeepLink?>(null)

    /** Splash stays up until settings load, so landing vs main is decided under it. */
    @Volatile private var contentReady = false

    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(com.aiyu.rewire.core.settings.AppLocale.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // This activity is recreated on every language change: re-word built-in warnings and channel names.
        warnings.relocalize()
        notifier.createChannels(this)
        splash.setKeepOnScreenCondition { !contentReady }
        splash.setOnExitAnimationListener { provider ->
            // Let Pause → Turn finish (API 31+; start is 0 below, so no wait), then exit.
            val remaining = provider.iconAnimationStartMillis + provider.iconAnimationDurationMillis -
                System.currentTimeMillis() // start is epoch-based
            provider.view.animate()
                .alpha(0f)
                .setStartDelay(remaining.coerceAtLeast(0))
                .setDuration(EXIT_MILLIS)
                .setInterpolator(EMPHASIZED_ACCELERATE)
                .withEndAction { provider.remove() }
                .start()
        }
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            deepLink = intent.deepLink()
            // Silent unless something newer exists; skipped when auto-update is off, snoozed or checked within the hour.
            if (BuildConfig.UPDATES) lifecycleScope.launch { updater.get().check() }
        }
        PlayUpdates.attach(this, offer = savedInstanceState == null) // Play builds only; no-op when sideloaded
        setContent {
            val settings by settingsState.collectAsStateWithLifecycle()
            val s = settings
            val mode = s?.themeMode ?: ThemeMode.SYSTEM
            val dark = mode == ThemeMode.DARK || (mode == ThemeMode.SYSTEM && isSystemInDarkTheme())
            // System bar icons follow the in-app theme, not only the system setting.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                onDispose { }
            }
            RewireTheme(themeMode = mode, dynamicColor = s?.dynamicColor ?: false) {
                // Surface (not a bare Box) so LocalContentColor is onSurface for every screen; otherwise text is black on dark.
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    // Wait for DataStore (a few ms) so landing vs main is decided once, without flicker.
                    if (s != null) {
                        SideEffect { contentReady = true }
                        RewireNavHost(
                            onboardingDone = s.onboardingDone,
                            deepLink = deepLink,
                            onDeepLinkConsumed = { deepLink = null },
                        )
                        if (BuildConfig.UPDATES && s.onboardingDone) UpdateHost()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        notifier.cancelFocusMinimised()
        if (BuildConfig.UPDATES) notifier.cancelUpdateAvailable() // the app is about to show the offer itself
        // Android refuses a foreground-service start from the background (process restarted by the system):
        // Rewire in front is the moment it is always allowed.
        engine.ensureMonitoring()
    }

    // Leaving mid-session (home, recents, another app) pops a heads-up so the user knows the timer runs on.
    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations) return
        notifier.focusMinimised(focus.current.value, System.currentTimeMillis())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.deepLink()?.let { deepLink = it }
    }

    private companion object {
        const val EXIT_MILLIS = 200L
        // M3 emphasized accelerate: elements leaving the screen.
        val EMPHASIZED_ACCELERATE = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)
    }

    private fun Intent.deepLink(): DeepLink? =
        getStringExtra(RewireNotifier.EXTRA_DEEP_LINK)?.let { runCatching { DeepLink.valueOf(it) }.getOrNull() }
}
