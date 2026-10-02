package com.rewire.app.core.guard

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.rewire.app.R

/**
 * Full-screen overlay (Display over apps) put up before the guard screen when Accessibility isn't running.
 *
 * Without Accessibility the guard starts from a background service, and Android / OEM skins may refuse that
 * start silently: the block was logged but the app stayed usable. An app with a visible window may start
 * activities, so the shield goes up first and the guard starts once it is on screen. If the start is still
 * refused, the shield covers the app itself and offers Go back / Open Rewire (a tap is always allowed).
 * Never a lockout: Go back is always there, and it removes itself after [TIMEOUT_MS].
 * Main thread only.
 */
object GuardShield {
    private const val SETTLE_MS = 150L
    private const val TIMEOUT_MS = 30_000L

    private val main = Handler(Looper.getMainLooper())
    private var view: View? = null
    private val expire = Runnable { dismiss() }

    /** Puts the shield up and runs [startGuard] once it's on screen. False = no overlay grant, nothing shown. */
    fun cover(context: Context, startGuard: () -> Unit, goHome: () -> Unit): Boolean {
        val app = context.applicationContext
        if (!Settings.canDrawOverlays(app)) return false
        dismiss()
        val wm = app.getSystemService(WindowManager::class.java) ?: return false
        val v = content(app, onBack = { dismiss(); goHome() }, onOpen = startGuard)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.OPAQUE,
        )
        if (runCatching { wm.addView(v, params) }.isFailure) return false
        view = v
        // Started once the window is drawn and visible to the system; earlier it doesn't count yet.
        v.post { main.postDelayed({ if (view === v) startGuard() }, SETTLE_MS) }
        main.removeCallbacks(expire)
        main.postDelayed(expire, TIMEOUT_MS)
        return true
    }

    /** Guard screen is up, or the user chose: take the shield down. */
    fun dismiss() {
        main.removeCallbacks(expire)
        val v = view ?: return
        view = null
        runCatching { v.context.getSystemService(WindowManager::class.java)?.removeView(v) }
    }

    private fun content(context: Context, onBack: () -> Unit, onOpen: () -> Unit): View {
        fun dp(value: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics).toInt()
        val onSurface = Color.WHITE
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(context.getColor(R.color.ic_launcher_background))
            setPadding(dp(32), dp(32), dp(32), dp(32))
            isClickable = true // swallow touches meant for the app underneath
            addView(TextView(context).apply {
                text = context.getString(R.string.guard_shield_title)
                setTextColor(onSurface)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                gravity = Gravity.CENTER
            })
            addView(TextView(context).apply {
                text = context.getString(R.string.guard_shield_text)
                setTextColor(onSurface)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, dp(24))
            })
            addView(Button(context).apply {
                text = context.getString(R.string.guard_shield_open)
                setOnClickListener { onOpen() }
            })
            addView(Button(context).apply {
                text = context.getString(R.string.guard_shield_back)
                setOnClickListener { onBack() }
            })
        }
    }
}
