package com.gridpointcode.wear

import android.app.Application
import android.graphics.Color
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.renderer.TileRenderer
import androidx.wear.tiles.testing.TestTileClient
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.time.Duration
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The tile, as a watch asks for it and draws it: the last place found, for an
 * hour, then the offer to open the app, both in one timeline so the watch need
 * not ask again to move from one to the other. Drawn by the Tiles renderer the
 * watch itself uses, on a round face, and kept as reference images.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = ROUND_WATCH)
class CodeTileTest {

    @get:Rule
    val roborazzi = RoborazziRule(options = RoborazziRule.Options(roborazziOptions = RoborazziOptions()))

    private val app: Application = ApplicationProvider.getApplicationContext()

    /** 13:41 UTC on 2 October 2026, held still so the time the tile shows is the same in every run. */
    private val now = 1_790_948_460_000L

    // The test client gives the tile its context and creates it, as the watch does.
    private val client by lazy { TestTileClient(CodeTile { now }, MoreExecutors.directExecutor()) }

    private val device = DeviceParametersBuilders.DeviceParameters.Builder()
        .setScreenWidthDp(227)
        .setScreenHeightDp(227)
        .setScreenDensity(2f)
        // Left out, it is nought, and every size that follows the reader's text
        // setting came out infinite: the code and the button's word drew as nothing.
        .setFontScale(1f)
        .setScreenShape(DeviceParametersBuilders.SCREEN_SHAPE_ROUND)
        .setDevicePlatform(DeviceParametersBuilders.DEVICE_PLATFORM_WEAR_OS)
        .build()

    private var zone: TimeZone? = null

    @Before
    fun holdTheZone() {
        zone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun giveTheZoneBack() {
        TimeZone.setDefault(zone)
    }

    /**
     * The answer, once the main thread has run the work that makes it. Under
     * Robolectric the test is the main thread, so waiting on it would wait on
     * itself.
     */
    private fun <T> ListenableFuture<T>.answered(): T {
        repeat(ANSWER_ROUNDS) {
            if (isDone) return get()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10))
        }
        return get()
    }

    private fun entries(): List<TimelineBuilders.TimelineEntry> =
        client.requestTile(RequestBuilders.TileRequest.Builder().setDeviceConfiguration(device).build()).answered()
            .tileTimeline!!.timelineEntries

    /** An entry drawn as the watch draws it, on a round face the size of one. */
    private fun drawn(entry: TimelineBuilders.TimelineEntry): ViewGroup {
        val resources = client.requestTileResourcesAsync(RequestBuilders.ResourcesRequest.Builder().setVersion("1").build()).answered()
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        // A watch's screen is black where the tile draws nothing.
        val face = FrameLayout(activity).apply { setBackgroundColor(Color.BLACK) }
        activity.setContentView(face, ViewGroup.LayoutParams(FACE_PX, FACE_PX))
        val renderer = TileRenderer(activity, MoreExecutors.directExecutor()) {}
        renderer.inflateAsync(entry.layout!!, resources ?: ResourceBuilders.Resources.Builder().setVersion("1").build(), face).answered()
        shadowOf(Looper.getMainLooper()).idle()
        return face
    }

    private fun View.words(): List<String> = when (this) {
        is TextView -> listOf(text.toString())
        is ViewGroup -> (0 until childCount).flatMap { getChildAt(it).words() }
        else -> emptyList()
    }

    @Test
    fun aRecentFixIsShownForItsHourThenTheOfferToOpenTheApp() {
        val found = LastFix("G3RJM8X3L1", 4, now - 10 * 60 * 1000L)
        LastFixStore(app).write(found)
        val (place, after) = entries()
        // The place until its hour is up; the offer from then on, to the end of
        // time: an entry left without an end ends at zero, and the watch drew
        // the tile empty.
        assertEquals(found.shownUntil, place.validity!!.endMillis)
        assertEquals(found.shownUntil, after.validity!!.startMillis)
        assertEquals(Long.MAX_VALUE, after.validity!!.endMillis)
        val words = drawn(place).words()
        assertTrue("the code in two halves: $words", "#G3RJM" in words && "8X3L1" in words)
        assertTrue("$words", "Open the app to find where you are." in drawn(after).words())
    }

    @Test
    fun withNoFixTheTileOffersToOpenTheApp() {
        val only = entries().single()
        assertNull(only.validity)
        assertTrue("Open the app to find where you are." in drawn(only).words())
    }

    @Test
    fun theTileWithAPlace() {
        LastFixStore(app).write(LastFix("G3RJM8X3L1", 4, now - 10 * 60 * 1000L))
        drawn(entries().first()).captureRoboImage()
    }

    @Test
    fun theTileWithNoPlace() {
        drawn(entries().single()).captureRoboImage()
    }

    private companion object {
        /** A round face 454 pixels across. */
        const val FACE_PX = 454

        /** Ten seconds of the main thread's time, in steps. */
        const val ANSWER_ROUNDS = 1_000
    }
}

/** A round watch, as the Pixel Watch is. */
const val ROUND_WATCH = "w227dp-h227dp-small-notlong-round-watch-xhdpi-keyshidden-nonav"
