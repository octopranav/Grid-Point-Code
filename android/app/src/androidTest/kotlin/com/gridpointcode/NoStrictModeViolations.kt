package com.gridpointcode

import android.os.Build
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Fails a test in which the app's own code broke StrictMode: read or wrote a
 * file or reached the network on the main thread, or left something open or
 * registered. The debug build keeps every violation (see DebugApplication);
 * this sorts them by who asked.
 *
 * Who asked is the first frame of code that is not the platform or the
 * language's own: a file read inside the app's `SavedPlaces` is the app's, while
 * one inside a library the app calls, Compose loading a font, the map loading its
 * cache, is that library's, and logged but not held against the app.
 */
object NoStrictModeViolations : TestRule {

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return base.evaluate()
            DebugApplication.violations.clear()
            base.evaluate()
            // One entry for each place in the app that broke it, however often.
            val ours = DebugApplication.violations
                .mapNotNull { violation -> asker(violation.stackTrace)?.let { (violation.javaClass.simpleName to it) to violation } }
                .groupBy({ it.first }, { it.second })
            if (ours.isNotEmpty()) {
                throw AssertionError(
                    ours.entries.joinToString("\n\n", prefix = "The app broke StrictMode in ${ours.size} places:\n") { (where, all) ->
                        "${where.first} at ${where.second}, ${all.size} times\n" +
                            all.first().stackTrace.take(FRAMES).joinToString("\n") { "    at $it" }
                    },
                )
            }
        }
    }

    /** The app's frame that asked, or null when it was not the app. */
    private fun asker(stack: Array<StackTraceElement>): StackTraceElement? {
        val asker = stack.firstOrNull { frame -> PLATFORM.none { frame.className.startsWith(it) } } ?: return null
        val ours = asker.className.startsWith(APP) && TESTS.none { asker.className.substringAfterLast('.').startsWith(it) }
        return asker.takeIf { ours }
    }

    private val PLATFORM = listOf("android.", "com.android.", "java.", "javax.", "kotlin.", "dalvik.", "libcore.", "sun.", "jdk.")
    private const val APP = "com.gridpointcode."

    /** The tests' own classes, which share the app's package. */
    private val TESTS = listOf("Reader", "ReadyDevice", "RecordOnFailure", "AccessibleKt", "NoStrictModeViolations")

    private const val FRAMES = 8
}
