package com.rewire.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.Until

const val PACKAGE = "com.rewire.app"
private const val TIMEOUT = 5_000L

/**
 * Fresh installs land on onboarding; walk through it once (state persists between iterations).
 * An interpreted (no-profile) build can take seconds to compose its first screen, so wait for either
 * onboarding or the main app rather than guessing how long that takes.
 */
fun MacrobenchmarkScope.passOnboardingIfShown() {
    val deadline = System.currentTimeMillis() + 15_000
    while (System.currentTimeMillis() < deadline) {
        if (device.hasObject(By.text("Guard"))) return // already past onboarding
        if (device.hasObject(By.text("Skip"))) break
        Thread.sleep(200)
    }
    if (!device.hasObject(By.text("Skip"))) return
    tap(By.text("Skip")) // straight to profile setup
    tap(By.text("Continue"))
    tap(By.text("Continue for now")) // permissions are optional; the journey skips granting them
    device.wait(Until.hasObject(By.text("Guard")), TIMEOUT)
}

/** Every tab, with the content transitions between them. */
fun MacrobenchmarkScope.switchTabs() {
    listOf("Focus", "Matrix", "Profile", "Guard").forEach(::tab)
}

/** Focus: start -> running ring -> fullscreen (shared timer digits) -> back -> end -> skip note. */
fun MacrobenchmarkScope.focusJourney() {
    tab("Focus")
    device.wait(Until.hasObject(By.text("Deep work, then real rest.")), TIMEOUT) // Guard has its own "Start focus"
    tap(By.text("Start focus"))
    tap(By.desc("Full screen timer"))
    device.waitForIdle()
    device.pressBack()
    // Ending is deliberate: press and hold past the 1.5 s fill.
    device.wait(Until.findObject(By.desc("Hold to end session")), TIMEOUT)?.click(2_000) ?: error("No hold-to-end")
    tap(By.text("Skip"))
    tap(By.text("Focus history"))
    device.waitForIdle()
    device.pressBack()
    tap(By.text("Back to setup"))
}

/** Profile sub-screens (large flexible app bar, lists). */
fun MacrobenchmarkScope.profileJourney() {
    tab("Profile")
    tap(By.text("Warning library"))
    device.wait(Until.hasObject(By.textStartsWith("Words that help")), TIMEOUT)
    device.wait(Until.findObject(By.scrollable(true)), TIMEOUT)?.fling(Direction.DOWN)
    device.waitForIdle()
    device.pressBack()
    tap(By.text("Notifications"))
    device.waitForIdle()
    device.pressBack()
    tab("Guard")
}

/** Bottom navigation item: the lowest on-screen node with that label (dashboards reuse the words). */
private fun MacrobenchmarkScope.tab(label: String) {
    device.wait(Until.hasObject(By.text(label)), TIMEOUT)
    retryStale { device.findObjects(By.text(label)).maxByOrNull { it.visibleBounds.top }?.click() ?: error("No tab $label") }
    device.waitForIdle()
}

/** Compose replaces nodes while animating; a node found a moment ago can go stale. Look again. */
private fun retryStale(block: () -> Unit) {
    repeat(3) { attempt ->
        try { return block() } catch (e: StaleObjectException) { if (attempt == 2) throw e }
    }
}

/** Waits for [selector]; if it's below the fold, scrolls the screen's scrollable to it. */
private fun MacrobenchmarkScope.tap(selector: BySelector) {
    retryStale {
        val target = device.wait(Until.findObject(selector), TIMEOUT)
            ?: device.findObject(By.scrollable(true))?.scrollUntil(Direction.DOWN, Until.findObject(selector))
            ?: error("Not found: $selector")
        target.click()
    }
    device.waitForIdle()
}
