package com.gridpointcode

import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.gridpointcode.core.aloud
import com.gridpointcode.core.formatted
import com.gridpointcode.core.spellingFor
import java.util.Locale

/**
 * The app as a reader drives it: by what the screen says and what a screen
 * reader hears, never by reaching into the code behind it. A test written this
 * way fails when a reader would be stuck, and survives a refactoring that a
 * reader would never notice.
 */
class Reader(private val rule: ComposeTestRule) {

    val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    fun text(id: Int, vararg args: Any): String = context.getString(id, *args)

    /** Waits for the app to settle and for [label]'s node to appear, up to [millis]. */
    fun waitFor(millis: Long = 10_000, label: () -> SemanticsNodeInteraction) {
        explained(millis) { runCatching { label().assertExists() }.isSuccess }
    }

    /** Waits until at least one node shows [text]: the same words can stand on the card and behind it. */
    fun waitForText(text: String, substring: Boolean = false, millis: Long = 10_000) {
        explained(millis, "\"$text\"") { rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
    }

    /**
     * Waits for [condition], and when it never comes, fails with what the
     * screen showed instead: every window's semantics, as a screen reader
     * would find them, so a failure in CI explains itself.
     */
    private fun explained(millis: Long, what: String = "the screen", condition: () -> Boolean) {
        try {
            rule.waitUntil(millis, condition)
        } catch (timeout: ComposeTimeoutException) {
            val screen = runCatching { rule.onAllNodes(isRoot()).printToString(maxDepth = Int.MAX_VALUE) }.getOrElse { "(could not be read: $it)" }
            throw AssertionError("Waited $millis ms for $what. The screen showed:\n$screen", timeout)
        }
    }

    /** Types into the field at the top of the map and presses Go. */
    fun search(text: String, go: Boolean = true) {
        val field = rule.onNode(hasSetTextAction() and hasText(text(R.string.search_label), substring = true))
        field.performClick()
        field.performTextClearance()
        field.performTextInput(text)
        if (go) {
            field.performImeAction()
            waitForKeyboardToClose()
        }
        rule.waitForIdle()
    }

    /**
     * Waits for the keyboard to finish going away. It slides off outside
     * Compose, so the app can be idle while the layout is still moving, and a
     * touch aimed at a button lands wherever the button was a moment before.
     */
    fun waitForKeyboardToClose() {
        rule.waitUntil(5_000) { !keyboardShown() }
        rule.waitForIdle()
    }

    private fun keyboardShown(): Boolean {
        var shown = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).firstOrNull()
            val view = activity?.window?.decorView
            shown = view != null && ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        return shown
    }

    /**
     * Touches what says [id]. It must be on the screen: a control pushed below
     * the edge still answers a test's click, at the corner of the screen, where
     * the map takes it as a place picked, and the test fails far from the cause.
     * Being on the screen is not being uncovered, so the sheet is lowered
     * before a map button is touched.
     */
    fun tap(id: Int) {
        rule.onNodeWithText(text(id)).assertIsDisplayed().performClick()
        rule.waitForIdle()
    }

    /** Touches the control a screen reader announces as [id], which must be on the screen. */
    fun tapDescribed(id: Int) {
        rule.onNodeWithContentDescription(text(id)).assertIsDisplayed().performClick()
        rule.waitForIdle()
    }

    /**
     * Brings [control] on to the screen as a reader would, the sheet opened and
     * scrolled until it shows, and touches it.
     */
    fun reach(control: SemanticsNodeInteraction) {
        openSheet()
        control.performScrollTo().assertIsDisplayed().performClick()
        rule.waitForIdle()
    }

    /** The code a screen reader hears on the place's card: its callouts, in the listener's words. */
    fun spoken(code: String): String = aloud(code, spellingFor(Locale.getDefault()))

    /** The card shows [code]: the mark is heard as that code's callouts. */
    fun assertShowing(code: String) {
        waitFor { rule.onNode(hasContentDescription(spoken(code))) }
        rule.onNode(hasContentDescription(spoken(code))).assertExists()
        audit()
    }

    /**
     * Keeps [code] when the app asks whether it was heard right. A code typed
     * far from the place on screen brings the codes one slip away from it above
     * the card, which pushes the card and the offer's own Keep below the edge
     * of the screen; a reader who meant the code reaches Keep, and the card
     * comes back up.
     */
    fun keepIfDoubted(code: String) {
        val keep = text(R.string.doubt_keep, formatted(code))
        if (rule.onAllNodesWithText(keep).fetchSemanticsNodes().isNotEmpty()) {
            reach(rule.onNodeWithText(keep))
            lowerSheet()
        }
    }

    /**
     * Opens the place's sheet over the map, as a screen reader's Expand does,
     * so what is below the card is on the screen to be touched. A tap on the
     * handle would close a sheet already open; Expand is offered only while it
     * is not. On a wide screen there is no sheet and nothing to open.
     */
    fun openSheet() {
        val closed = rule.onAllNodes(hasContentDescription("Drag handle") and SemanticsMatcher.keyIsDefined(SemanticsActions.Expand))
        if (closed.fetchSemanticsNodes().isNotEmpty()) {
            closed.onFirst().performSemanticsAction(SemanticsActions.Expand)
            rule.waitForIdle()
        }
    }

    /**
     * Lowers the sheet to where it rests, as a screen reader's Collapse does,
     * so the map and its buttons are uncovered again. An open sheet lies over
     * them, and a touch meant for one lands on the sheet instead.
     */
    fun lowerSheet() {
        val open = rule.onAllNodes(hasContentDescription("Drag handle") and SemanticsMatcher.keyIsDefined(SemanticsActions.Collapse))
        if (open.fetchSemanticsNodes().isNotEmpty()) {
            open.onFirst().performSemanticsAction(SemanticsActions.Collapse)
            rule.waitForIdle()
        }
    }

    /** Scrolls the panel until [label] is on the screen. */
    fun scrollTo(label: String) {
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(label))
    }

    /** Runs a shell command as the test's shell, for the settings a reader changes outside the app. */
    fun shell(command: String): String =
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { output ->
            // Read to the end: closing early can end the command before it has run.
            ParcelFileDescriptor.AutoCloseInputStream(output).bufferedReader().readText()
        }

    /** Runs the accessibility checks over the whole screen now, not only after an action. */
    fun audit() {
        rule.waitForIdle()
        rule.onRoot().tryPerformAccessibilityChecks()
    }

    /**
     * The device reports a fix at [latitude], [longitude] through every provider
     * the app listens to, again every half second as a receiver outdoors does:
     * a test provider gives a listener only what is sent after it starts.
     */
    @Suppress("DEPRECATION")
    fun fix(latitude: Double, longitude: Double, accuracy: Float = 4f) {
        shell("appops set ${context.packageName} android:mock_location allow")
        shell("cmd location set-location-enabled true")
        val manager = context.getSystemService(LocationManager::class.java)
        val providers = buildList {
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
        }
        for (provider in providers) {
            runCatching { manager.removeTestProvider(provider) }
            manager.addTestProvider(
                provider, false, false, false, false, true, true, true,
                Criteria.POWER_LOW, Criteria.ACCURACY_FINE,
            )
            manager.setTestProviderEnabled(provider, true)
        }
        reporting?.interrupt()
        reporting = Thread {
            try {
                while (!Thread.currentThread().isInterrupted) {
                    for (provider in providers) {
                        manager.setTestProviderLocation(
                            provider,
                            Location(provider).apply {
                                this.latitude = latitude
                                this.longitude = longitude
                                this.accuracy = accuracy
                                time = System.currentTimeMillis()
                                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                            },
                        )
                    }
                    Thread.sleep(500)
                }
            } catch (_: InterruptedException) {
            } catch (_: SecurityException) {
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private var reporting: Thread? = null
}
