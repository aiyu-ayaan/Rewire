package com.rewire.app.feature.guard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewire.app.core.apps.InstalledAppsSource
import com.rewire.app.core.guard.HabitEngine
import com.rewire.app.core.settings.Settings
import com.rewire.app.data.HabitRepository
import com.rewire.app.data.WarningRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import com.rewire.app.core.settings.ThemeMode
import com.rewire.app.core.guard.GuardOutcome
import com.rewire.app.core.guard.GuardShield
import com.rewire.app.domain.habit.WarningLevel
import com.rewire.app.domain.warning.WarningPicker
import com.rewire.app.ui.theme.RewireTheme

/**
 * The real warning / block screen, launched by HabitEngine over the protected app.
 * Own task, excluded from recents. Every exit path reports an outcome so nothing is silently skipped.
 */
@AndroidEntryPoint
class GuardActivity : FragmentActivity() {
    @Inject lateinit var engine: HabitEngine
    @Inject lateinit var settingsState: StateFlow<Settings?>
    @Inject lateinit var habitRepo: HabitRepository
    @Inject lateinit var warningRepo: WarningRepository
    @Inject lateinit var installedApps: InstalledAppsSource

    private data class Request(val pkg: String, val habitId: String, val level: WarningLevel, val blockReason: String?)

    private var request by mutableStateOf<Request?>(null)
    private var resolved = false
    /** System auth screen (PIN/pattern) stops us; that's not the user abandoning the block. */
    private var authenticating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instances++
        enableEdgeToEdge()
        // Android always lists the task that is on screen in Recents (excludeFromRecents applies once it's gone)
        // and captures it as Recents opens, before any lifecycle callback. A secure window is shown blank there,
        // so what was blocked is never on display. Also blanks screenshots of this one screen.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        request = intent.toRequest() ?: return finish()
        onBackPressedDispatcher.addCallback(this) { goBack() }
        setContent {
            val settings by settingsState.collectAsStateWithLifecycle()
            val habits by habitRepo.habits.collectAsStateWithLifecycle()
            val warnings by warningRepo.warnings.collectAsStateWithLifecycle()
            val req = request ?: return@setContent
            val profile = habits.find { it.id == req.habitId } ?: return@setContent
            val shown = profile.copy(rule = profile.rule.copy(warningLevel = req.level))
            val warning = remember(req) { WarningPicker.pick(warnings, req.level) }
            val label = remember(req.pkg) { installedApps.label(req.pkg) }
            val userReason = settings?.profile?.reason?.takeIf { it.isNotBlank() }
            RewireTheme(themeMode = settings?.themeMode ?: ThemeMode.SYSTEM, dynamicColor = settings?.dynamicColor ?: false) {
                WarningScreen(
                    profile = shown, warning = warning, packageName = req.pkg, appLabel = label, preview = false,
                    userReason = userReason,
                    onGoBack = ::goBack,
                    onContinue = { if (req.level == WarningLevel.MAX) authenticateEmergency() else proceed(GuardOutcome.CONTINUED) },
                    blockReason = req.blockReason,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.toRequest()?.let { request = it; resolved = false }
    }

    private fun goBack() {
        val r = request ?: return finish()
        resolve(r, GuardOutcome.WENT_BACK)
        finish() // Home is already underneath
    }

    private fun proceed(outcome: GuardOutcome) {
        val r = request ?: return finish()
        resolve(r, outcome)
        finish() // engine relaunches the app for this one visit
    }

    /**
     * Emergency unlock = deliberate: the device's own screen lock (fingerprint/face or PIN/pattern/password).
     * No screen lock set up -> the confirm dialog already shown is the only gate (can't require what doesn't exist).
     */
    private fun authenticateEmergency() {
        val authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            proceed(GuardOutcome.EMERGENCY_ONCE)
            return
        }
        authenticating = true
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                authenticating = false
                proceed(GuardOutcome.EMERGENCY_ONCE)
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                authenticating = false // cancelled / failed: stay blocked
            }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Confirm emergency unlock")
                .setSubtitle("Opens this app once and records an override")
                .setAllowedAuthenticators(authenticators)
                .build()
        )
    }

    override fun onResume() {
        super.onResume()
        GuardShield.dismiss() // the guard is on screen now; the overlay only bridged its start
    }

    override fun onStop() {
        super.onStop()
        // Home / recents / screen off without choosing: close. Next open of the app is judged fresh.
        if (!isChangingConfigurations && !resolved && !authenticating) {
            request?.let { resolve(it, GuardOutcome.ABANDONED) }
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instances--
        // Finished or killed by the system before the user chose: still report, or the engine waits forever.
        if (!isChangingConfigurations && !resolved) request?.let { resolve(it, GuardOutcome.ABANDONED) }
    }

    private fun resolve(r: Request, outcome: GuardOutcome) {
        if (resolved) return
        resolved = true
        engine.onGuardResult(r.pkg, r.habitId, r.level, outcome)
    }

    private fun Intent.toRequest(): Request? {
        val pkg = getStringExtra(EXTRA_PKG) ?: return null
        val habit = getStringExtra(EXTRA_HABIT) ?: return null
        val level = getStringExtra(EXTRA_LEVEL)?.let { runCatching { WarningLevel.valueOf(it) }.getOrNull() } ?: return null
        return Request(pkg, habit, level, getStringExtra(EXTRA_REASON))
    }

    companion object {
        /** Live screens; main thread only. Lets the engine tell a pending guard from one the system dropped. */
        var instances = 0
            private set

        private const val EXTRA_PKG = "pkg"
        private const val EXTRA_HABIT = "habit"
        private const val EXTRA_LEVEL = "level"
        private const val EXTRA_REASON = "reason"

        fun intent(context: Context, pkg: String, habitId: String, level: WarningLevel, blockReason: String?) =
            Intent(context, GuardActivity::class.java)
                .putExtra(EXTRA_PKG, pkg).putExtra(EXTRA_HABIT, habitId)
                .putExtra(EXTRA_LEVEL, level.name).putExtra(EXTRA_REASON, blockReason)
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}
