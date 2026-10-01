package com.gridpointcode

import android.app.KeyguardManager
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Puts the device in the state a reader's phone is in when they open the app:
 * awake, unlocked, and with nothing of the system's over it. An emulator booted
 * fresh, as CI's is for every run, starts on its lock screen, and one drawing in
 * software is slow enough that System UI is often reported as not responding.
 * Either holds the focus, and without it the keyboard does not open, the
 * clipboard reads as empty and the system's own questions cannot be reached.
 */
object ReadyDevice : TestRule {

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            ready()
            base.evaluate()
        }
    }

    private fun ready() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        // No dialog for a slow system app from here on, and no lock screen the
        // next time the screen goes off.
        device.executeShellCommand("settings put global hide_error_dialogs 1")
        device.executeShellCommand("locksettings set-disabled true")
        device.wakeUp()
        // A dialog already showing is answered as a reader would, by waiting.
        device.findObject(By.res("android:id/aerr_wait"))?.click()
        // A lock screen already up goes with the menu key, as on an emulator it does.
        val keyguard = instrumentation.targetContext.getSystemService(KeyguardManager::class.java)
        if (keyguard.isKeyguardLocked) {
            device.executeShellCommand("wm dismiss-keyguard")
            device.pressMenu()
            val until = SystemClock.uptimeMillis() + UNLOCK_MS
            while (keyguard.isKeyguardLocked && SystemClock.uptimeMillis() < until) SystemClock.sleep(100)
            check(!keyguard.isKeyguardLocked) { "The lock screen would not go." }
        }
        device.waitForIdle()
    }

    private const val UNLOCK_MS = 5_000L
}
