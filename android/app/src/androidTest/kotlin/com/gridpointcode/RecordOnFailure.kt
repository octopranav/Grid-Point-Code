package com.gridpointcode

import android.app.KeyguardManager
import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * When a test fails, adds to its failure what the whole device showed: every
 * window on the screen, whose it is, which one holds the focus and the words in
 * it, and whether the lock screen is up. The Compose dump in a failure's message
 * sees only the app; this reaches a system dialog over it or a window that took
 * the focus, which is what a test that passes on one emulator and fails on
 * another usually comes down to.
 *
 * A screenshot is kept too, where Gradle collects a test's extra output. The
 * app's data is cleared before every test and that folder with it, so the
 * screenshot survives only from the last test of a run, as when one test is run
 * on its own to look into it.
 */
class RecordOnFailure : TestRule {

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            try {
                base.evaluate()
            } catch (failure: Throwable) {
                runCatching { failure.addSuppressed(WhatTheDeviceShowed(describeTheDevice())) }
                runCatching { keepScreenshot("${description.testClass.simpleName}-${description.methodName}") }
                throw failure
            }
        }
    }

    private class WhatTheDeviceShowed(screen: String) : Exception(screen) {
        override fun fillInStackTrace(): Throwable = this
    }

    private fun describeTheDevice(): String {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // UiDevice asks for every window, not only the one in front.
        UiDevice.getInstance(instrumentation)
        val locked = instrumentation.targetContext.getSystemService(KeyguardManager::class.java).isKeyguardLocked
        val windows = instrumentation.uiAutomation.windows.joinToString("\n") { window ->
            val root = window.root
            val flags = listOfNotNull("focused".takeIf { window.isFocused }, "active".takeIf { window.isActive })
            "  ${kindOf(window)} ${root?.packageName ?: "?"} \"${window.title ?: ""}\" ${flags.joinToString(" ")}: " +
                (root?.let { wordsIn(it).take(WORDS).joinToString(" | ") } ?: "")
        }
        return "The device showed, front to back, with the lock screen ${if (locked) "up" else "down"}:\n$windows"
    }

    private fun kindOf(window: AccessibilityWindowInfo): String = when (window.type) {
        AccessibilityWindowInfo.TYPE_APPLICATION -> "app"
        AccessibilityWindowInfo.TYPE_SYSTEM -> "system"
        AccessibilityWindowInfo.TYPE_INPUT_METHOD -> "keyboard"
        AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> "overlay"
        else -> "window"
    }

    private fun wordsIn(node: AccessibilityNodeInfo): Sequence<String> = sequence {
        val words = node.text ?: node.contentDescription
        if (!words.isNullOrBlank()) yield(words.toString())
        for (i in 0 until node.childCount) node.getChild(i)?.let { yieldAll(wordsIn(it)) }
    }

    private fun keepScreenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        val screen = instrumentation.uiAutomation.takeScreenshot() ?: return
        // Asking for the app's media folders makes the one the folder is in.
        instrumentation.targetContext.externalMediaDirs
        File(folder).apply { mkdirs() }.resolve("$name.png").outputStream().use { screen.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val WORDS = 16
    }
}
