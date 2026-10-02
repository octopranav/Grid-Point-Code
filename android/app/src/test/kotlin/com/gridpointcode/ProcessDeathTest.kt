package com.gridpointcode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gridpointcode.core.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/**
 * The app as Android leaves it in the background. To have the memory back, the
 * system ends the process; when the reader comes back, a new activity and a new
 * view model are made from what the old activity saved. Here that is done
 * exactly, through the platform's own saved state: the first activity saves and
 * is thrown away, and a second is made from its saved state alone.
 *
 * Before the place was saved, a reader came back to the specification's example:
 * having gone to a messaging app to paste a code, or to the telephone with the
 * emergency card open, they lost what they were reading.
 *
 * Not on a device: "Don't keep activities" set from a shell is not seen by the
 * system there, and the activity only stops, so a test on it passes whether or
 * not anything is saved.
 */
@RunWith(AndroidJUnit4::class)
class ProcessDeathTest {

    @Before
    fun noConnection() = NoConnection.turnAwayEveryRequest()

    /** The view model the reader comes back to, after [before] was left in the state [set] puts it in. */
    private fun afterProcessDeath(set: PlaceViewModel.() -> Unit): PlaceViewModel {
        val first = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val before = ViewModelProvider(first.get())[PlaceViewModel::class.java]
        before.set()
        val saved = Bundle()
        first.pause().stop().saveInstanceState(saved).destroy()
        val second = Robolectric.buildActivity(ComponentActivity::class.java).setup(saved)
        val after = ViewModelProvider(second.get())[PlaceViewModel::class.java]
        assertNotSame("a new view model, as in a new process", before, after)
        return after
    }

    @Test
    fun thePlaceAndItsDirectionsAreStillThere() {
        val after = afterProcessDeath { open("https://gridpointcode.com/play?c=$MARKET&n=North+door", Source.LINK) }
        assertEquals(MARKET, after.ui.value.selection.code)
        assertEquals(Source.LINK, after.ui.value.selection.source)
        assertEquals("North door", after.ui.value.note)
    }

    @Test
    fun theEmergencyCardIsStillOpen() {
        assertTrue(afterProcessDeath { openEmergency() }.emergency.value)
    }

    @Test
    fun aCodeBeingTypedIsStillThere() {
        assertEquals("G3RJM-8X", afterProcessDeath { type("G3RJM-8X") }.query)
    }

    @Test
    fun withNothingSavedTheAppOpensOnTheExample() {
        val fresh = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val model = ViewModelProvider(fresh.get())[PlaceViewModel::class.java]
        assertEquals(Source.SAMPLE, model.ui.value.selection.source)
        assertFalse(model.emergency.value)
    }

    private companion object {
        /** St Lawrence Market's north door. */
        const val MARKET = "G3RJM8X3L1"
    }
}
