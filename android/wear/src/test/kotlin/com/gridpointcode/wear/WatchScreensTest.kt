package com.gridpointcode.wear

import android.Manifest
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.compose.material3.TimeSource
import androidx.wear.compose.material3.TimeText
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.gridpointcode.core.Point
import com.gridpointcode.core.SavedPlace
import com.gridpointcode.core.encodeSaved
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The watch's screens on a round face, for the design's reference images: the
 * code where the wrist is, the saved places with their arrows, and walking to
 * one. The time along the top is held at ten past ten, and the saved places are
 * the ones the watch keeps in its file when the phone is out of reach.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = ROUND_WATCH)
class WatchScreensTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    @get:Rule
    val roborazzi = RoborazziRule(options = RoborazziRule.Options(roborazziOptions = RoborazziOptions()))

    private val app: Application = ApplicationProvider.getApplicationContext()

    private val market = SavedPlace("G3RJM8X3L1", "Market, north door", "North door, beside the bakery stall", savedAt = 3)
    private val tower = SavedPlace("G3RJM0M67J", "Tower base", "", savedAt = 2)
    private val kensington = SavedPlace("G3RJL5FRCR", "Kensington Market", "", savedAt = 1)

    /** At Union Station, to within four metres. */
    private val union = WatchFix(Point(43.64546, -79.38063), 4, at = 1_790_948_460_000L)

    /** Ten past ten, the hour a watch is shown at. */
    private val stillTime: @Composable () -> Unit = {
        TimeText(timeSource = object : TimeSource {
            @Composable
            override fun currentTime(): String = "10:09"
        })
    }

    /**
     * Opens the watch app with location [permitted] or not, the [saved] places
     * in its file, and [fix] where the watch is, if found.
     */
    private fun open(permitted: Boolean, saved: List<SavedPlace> = emptyList(), fix: WatchFix? = null) {
        if (permitted) shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        File(app.filesDir, WatchShelf.CACHE).writeText(encodeSaved(saved))
        ActivityScenario.launch(ComponentActivity::class.java).onActivity { activity ->
            // Made by a factory of this test's own: the default one is kept for
            // the whole process, which Robolectric keeps from test to test, and
            // it handed every later test the first test's application and files.
            val model = ViewModelProvider(activity, ViewModelProvider.AndroidViewModelFactory(app))[WatchModel::class.java]
            activity.setContent { WatchApp(model, WatchSpeaker(activity), onAllow = {}, timeText = stillTime) }
            fix?.let(model::arrived)
        }
        rule.waitForIdle()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun record() {
        rule.waitForIdle()
        captureScreenRoboImage()
    }

    @Test
    fun hereAskingForLocation() {
        open(permitted = false)
        record()
    }

    @Test
    fun hereFinding() {
        open(permitted = true)
        record()
    }

    @Test
    fun hereWithAPlace() {
        open(permitted = true, fix = union)
        record()
    }

    @Test
    fun savedPlacesNearestFirst() {
        open(permitted = true, saved = listOf(market, tower, kensington), fix = union)
        rule.onNodeWithText(app.getString(R.string.saved_open)).performClick()
        record()
    }

    @Test
    fun walkingToASavedPlace() {
        open(permitted = true, saved = listOf(market, tower, kensington), fix = union)
        rule.onNodeWithText(app.getString(R.string.saved_open)).performClick()
        rule.onNodeWithText("Market, north door").performClick()
        record()
    }
}
