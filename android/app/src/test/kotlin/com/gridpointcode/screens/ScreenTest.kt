package com.gridpointcode.screens

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.pranavpatel.algo.gridpointcode.design.GpcTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.gridpointcode.Anchoring
import com.gridpointcode.PlaceScreen
import com.gridpointcode.PlaceViewModel
import com.gridpointcode.SavedShelf
import com.gridpointcode.Speaker
import com.gridpointcode.core.Emergency
import com.gridpointcode.core.Point
import com.gridpointcode.core.Source
import com.gridpointcode.core.emergencyOf
import com.gridpointcode.core.selectionAt
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

/**
 * The app's screens drawn on the computer, for the design's reference images:
 * the real screen, view model and resources, drawn by Android's own graphics
 * stack under Robolectric. Each test brings the app to one state and records the
 * whole screen, dialogs included; CI compares every screen with the image kept
 * in `src/test/screenshots` and fails on any that no longer matches.
 *
 * What could make one run's screen differ from the next is held still. The map,
 * which cannot be drawn on a computer, is the stand-in PlaceMap draws in
 * inspection mode. There is no connection, so nothing the site serves can change
 * a screen, and the landmarks are Toronto's shard as the app caches it after a
 * look around. The saved list starts empty.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreenTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    /**
     * Half size: a moved control or a changed colour still shows, and the
     * images kept in the repository stay small.
     */
    @get:Rule
    val roborazzi = RoborazziRule(
        options = RoborazziRule.Options(
            roborazziOptions = RoborazziOptions(recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5)),
        ),
    )

    protected lateinit var model: PlaceViewModel
        private set

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    /** Opens the app at [fontScale], does [first] to it, and waits for it to settle. */
    protected fun open(fontScale: Float = 1f, first: PlaceViewModel.() -> Unit = {}) {
        noConnection()
        if (fontScale != 1f) RuntimeEnvironment.setFontScale(fontScale)
        lookedAroundToronto()
        // The saved list is the app's one list for the whole process, and
        // Robolectric keeps the process from one test to the next.
        SavedShelf.change { emptyList() }
        ActivityScenario.launch(ComponentActivity::class.java).onActivity { activity ->
            activity.enableEdgeToEdge()
            model = ViewModelProvider(activity)[PlaceViewModel::class.java]
            val speaker = Speaker(activity)
            activity.setContent {
                GpcTheme {
                    CompositionLocalProvider(LocalInspectionMode provides true) {
                        PlaceScreen(model = model, speaker = speaker)
                    }
                }
            }
            model.first()
        }
        settle()
    }

    /**
     * Waits until the landmarks near the place on screen, and whether its area
     * is kept, are worked out. Both are read off the main thread, where the
     * test's own idling does not look.
     */
    protected fun settle() {
        rule.waitForIdle()
        try {
            rule.waitUntil(SETTLE_MS) {
                // The answers come back to the main thread, which is let run here.
                shadowOf(Looper.getMainLooper()).idle()
                val anchoring = model.anchors.value
                anchoring.code == model.ui.value.selection.code &&
                    anchoring.status != Anchoring.Status.LOOKING &&
                    model.offline.value.known
            }
        } catch (timeout: ComposeTimeoutException) {
            throw AssertionError(
                "The screen never settled: showing ${model.ui.value.selection.code}, landmarks " +
                    "${model.anchors.value.status} for ${model.anchors.value.code}, offline known ${model.offline.value.known}.",
                timeout,
            )
        }
        rule.waitForIdle()
    }

    /** Records the whole screen, dialogs included, under the test's own name. */
    @OptIn(ExperimentalRoborazziApi::class)
    protected fun record() {
        settle()
        captureScreenRoboImage()
    }

    /**
     * Records a dialog that opens with a text field. Its cursor blinks for as
     * long as it is open, so the screen never goes still: the test's clock is
     * stopped and stepped on by hand instead, to the same moment every run.
     */
    @OptIn(ExperimentalRoborazziApi::class)
    protected fun recordTyping(opens: () -> Unit) {
        settle()
        rule.mainClock.autoAdvance = false
        opens()
        // Frame by frame, with the main thread let run between them, where the
        // dialog's own window is laid out.
        repeat(DIALOG_FRAMES) {
            rule.mainClock.advanceTimeByFrame()
            shadowOf(Looper.getMainLooper()).idle()
        }
        captureScreenRoboImage()
    }

    protected fun text(id: Int, vararg args: Any): String = app.getString(id, *args)

    protected fun tap(id: Int) {
        rule.onAllNodesWithText(text(id)).onFirst().performClick()
        rule.waitForIdle()
    }

    protected fun tapDescribed(id: Int) {
        rule.onNodeWithContentDescription(text(id)).performClick()
        if (rule.mainClock.autoAdvance) rule.waitForIdle()
    }

    /** Opens the sheet over the map all the way, as a screen reader's Expand does. */
    protected fun openSheet() {
        // Found by what it does, not what it is called: the name is translated.
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand))
            .performSemanticsAction(SemanticsActions.Expand)
        rule.waitForIdle()
    }

    /**
     * The device has found the reader at [latitude], [longitude], to within two
     * metres: inside one cell, so the fix is the place and listening has
     * stopped, the state the emergency card settles in.
     *
     * Put in place through the view model rather than sent through a stand-in
     * location provider. Compose's test runner queues the card's own listening
     * where nothing runs it, though on a device it starts at once; the device
     * tests cover that path, with the emulator's provider.
     */
    protected fun foundAt(latitude: Double, longitude: Double) {
        rule.waitForIdle()
        model.place(selectionAt(Point(latitude, longitude), Source.DEVICE, accuracyMetres = 2.0))
        rule.waitForIdle()
        val card = emergencyOf(model.ui.value)
        check(card is Emergency.Here) { "The card is not showing the fix but $card." }
    }

    /**
     * Puts Toronto's shard where the app caches the shards it met while looking
     * around, as it is after a reader has looked at Toronto with a connection.
     */
    private fun lookedAroundToronto() {
        val seen = File(app.cacheDir, "seen-landmarks")
        File(seen, "shards").mkdirs()
        File(seen, "manifest.json").writeText("""{"level":4,"built":"2026-09-27T16:21:39.176Z"}""")
        val shard = checkNotNull(javaClass.getResourceAsStream("/landmarks/G3RJ.json")) { "The Toronto shard is missing." }
        File(seen, "shards/G3RJ.json").writeBytes(shard.use { it.readBytes() })
    }

    /**
     * No connection: every request goes to a proxy on this computer that turns
     * it away at once, so what the site serves today can never change a screen.
     * Turned away rather than sent to a closed port, which Windows takes two
     * seconds to refuse, each time.
     */
    private fun noConnection() {
        val port = TurnedAway.port
        for (scheme in listOf("http", "https")) {
            System.setProperty("$scheme.proxyHost", "127.0.0.1")
            System.setProperty("$scheme.proxyPort", port.toString())
        }
        System.setProperty("http.nonProxyHosts", "")
    }

    /** A proxy, reachable only from this computer, that answers everything with 503. */
    private object TurnedAway {
        val port: Int by lazy {
            val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
            thread(isDaemon = true, name = "turned-away") {
                while (true) {
                    val socket = runCatching { server.accept() }.getOrNull() ?: break
                    runCatching {
                        socket.use { it.getOutputStream().write(UNAVAILABLE) }
                    }
                }
            }
            server.localPort
        }

        private val UNAVAILABLE =
            "HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()
    }

    private companion object {
        const val SETTLE_MS = 30_000L

        /** Long enough for a dialog to open and its field to take the focus: a second. */
        const val DIALOG_FRAMES = 60
    }
}
