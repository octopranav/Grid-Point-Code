package com.gridpointcode.benchmark

import android.app.KeyguardManager
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

/** The phone app, as the benchmarks install it. */
const val APP = "com.gridpointcode"

/**
 * The device as a reader holds it: awake, unlocked, with no system dialog over
 * the app. An emulator Gradle has just made starts on its lock screen, and one
 * drawing in software often reports System UI as not responding; either hides
 * the app from every journey. The same as the device tests' ReadyDevice.
 */
fun MacrobenchmarkScope.readyTheDevice() {
    device.executeShellCommand("settings put global hide_error_dialogs 1")
    device.executeShellCommand("locksettings set-disabled true")
    device.wakeUp()
    device.findObject(By.res("android:id/aerr_wait"))?.click()
    val keyguard = InstrumentationRegistry.getInstrumentation().context.getSystemService(KeyguardManager::class.java)
    if (keyguard.isKeyguardLocked) {
        device.executeShellCommand("wm dismiss-keyguard")
        device.pressMenu()
        device.wait(Until.gone(By.pkg("com.android.systemui").res("com.android.systemui:id/keyguard_root_view")), WAIT_MS)
    }
}

/**
 * The card is showing a place: its code is heard as callouts. Every code the
 * journeys reach is in Toronto, and so begins with the same words.
 */
fun MacrobenchmarkScope.waitForTheCard() {
    check(device.wait(Until.hasObject(By.descContains(TORONTO_CALLOUTS)), WAIT_MS)) { "The card never showed a place." }
}

/** Types [code] into the field at the top of the map and goes to it. */
fun MacrobenchmarkScope.goTo(code: String) {
    val field = checkNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), WAIT_MS)) { "No field to type in." }
    field.click()
    field.text = code
    device.pressEnter()
    device.waitForIdle()
    waitForTheCard()
}

/** Drags the sheet up over the map, as a reader pulls it to see everything about the place. */
fun MacrobenchmarkScope.openThePanel() {
    val handle = device.wait(Until.findObject(By.desc("Drag handle")), WAIT_MS) ?: return
    handle.drag(android.graphics.Point(handle.visibleCenter.x, device.displayHeight / 6), DRAG_STEPS)
    device.waitForIdle()
}

/** Down the panel and back up, as a reader looks through it. */
fun MacrobenchmarkScope.scrollThePanel() {
    val panel = device.findObject(By.scrollable(true)) ?: return
    panel.setGestureMargin(device.displayWidth / 5)
    panel.fling(Direction.DOWN)
    device.waitForIdle()
    panel.fling(Direction.UP)
    device.waitForIdle()
}

/** Saved, then Settings, looked through, then back to the map. */
fun MacrobenchmarkScope.visitTheTabs() {
    for (tab in listOf("Saved", "Settings")) {
        device.wait(Until.findObject(By.text(tab)), WAIT_MS)?.click()
        device.waitForIdle()
    }
    device.findObject(By.scrollable(true))?.let { page ->
        page.setGestureMargin(device.displayWidth / 5)
        page.fling(Direction.DOWN)
        device.waitForIdle()
    }
    device.wait(Until.findObject(By.text("Map")), WAIT_MS)?.click()
    device.waitForIdle()
}

/** St Lawrence Market's north door. */
const val MARKET = "G3RJM8X3L1"

/** How every code in Toronto is heard, to begin with. */
private const val TORONTO_CALLOUTS = "Golf, three, Romeo, Juliett"

private const val WAIT_MS = 10_000L
private const val DRAG_STEPS = 20
