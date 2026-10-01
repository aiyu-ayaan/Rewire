package com.rewire.app

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewire.app.core.datastore.ThemeMode
import com.rewire.app.core.notifications.DeepLink
import com.rewire.app.core.notifications.RewireNotifier
import com.rewire.app.ui.RewireNavHost
import com.rewire.app.ui.theme.RewireTheme

class MainActivity : ComponentActivity() {

    private var deepLink by mutableStateOf<DeepLink?>(null)

    /** Splash stays up until settings load, so landing vs main is decided under it. */
    @Volatile private var contentReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
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
        if (savedInstanceState == null) deepLink = intent.deepLink()
        val container = (application as RewireApp).container
        setContent {
            val settings by container.settings.collectAsStateWithLifecycle()
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
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    // Wait for DataStore (a few ms) so landing vs main is decided once, without flicker.
                    if (s != null) {
                        SideEffect { contentReady = true }
                        RewireNavHost(
                            onboardingDone = s.onboardingDone,
                            deepLink = deepLink,
                            onDeepLinkConsumed = { deepLink = null },
                        )
                    }
                }
            }
        }
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
