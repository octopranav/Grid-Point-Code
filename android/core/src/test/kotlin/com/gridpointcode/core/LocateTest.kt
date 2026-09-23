package com.gridpointcode.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * What a stream of device fixes may do to the place, and what it may not.
 *
 * A fix arrives whenever the device has one, not when the reader wants it, so
 * each rule here is about a fix turning up at a bad moment: late, looser than
 * the one before, or after the reader has already chosen somewhere else.
 */
class LocateTest {

    private val start = PlaceState(selectionAt(Point(43.650006, -79.380004), Source.SAMPLE))
    private val union = Point(43.645306, -79.380588)
    private val market = Point(43.6487, -79.3716)

    @Test
    fun aFixNobodyAskedForIsIgnored() {
        assertSame(start, start.located(union, 5.0))
    }

    @Test
    fun theFirstFixIsANewPlace() {
        val described = start.described("Blue gate")
        val here = described.locating().located(union, 12.0)
        assertEquals(Source.DEVICE, here.selection.source)
        assertEquals("G3RJM0TJ0G", here.selection.code)
        assertEquals(12.0, here.selection.accuracyMetres)
        assertEquals("", here.note, "directions for the old place must not follow the reader")
        assertEquals(Locating.REFINING, here.locating)
    }

    @Test
    fun aTighterFixReplacesThePointAndKeepsWhatWasTyped() {
        val first = start.locating().located(union, 12.0).described("Side door")
        val better = first.located(Point(43.645310, -79.380590), 6.0)
        assertEquals(6.0, better.selection.accuracyMetres)
        assertEquals("Side door", better.note)
        assertEquals(Locating.REFINING, better.locating)
    }

    @Test
    fun aLooserFixNeverUndoesABetterOne() {
        val first = start.locating().located(union, 6.0)
        assertSame(first, first.located(market, 40.0))
    }

    @Test
    fun listeningEndsOnceAFixIsInsideOneCell() {
        val settled = start.locating().located(union, 12.0).located(union, 2.6)
        assertEquals(Locating.IDLE, settled.locating)
        assertEquals(3, fixOf(settled.selection)?.metres)
        assertSame(settled, settled.located(market, 1.0), "a fix after listening ended is late")
    }

    @Test
    fun pressingTheButtonAgainStartsAgain() {
        // The reader may have walked a kilometre since the last press, so the
        // first fix of a new press is taken even though it is looser.
        val earlier = start.locating().located(union, 3.0)
        val again = earlier.locating().located(market, 20.0)
        assertEquals(20.0, again.selection.accuracyMetres)
        assertEquals("G3RJM8X6TX", again.selection.code)
    }

    @Test
    fun choosingAnotherPlaceStopsListening() {
        val refining = start.locating().located(union, 12.0)
        assertEquals(Locating.IDLE, refining.opened("G3RJL5FRCR", Source.CODE).locating)
        assertEquals(Locating.IDLE, refining.placed(selectionAt(market, Source.MAP)).locating)
        assertEquals(Locating.IDLE, refining.nudged(Compass.N).locating)
    }

    @Test
    fun aFixThatArrivesAfterANudgeDoesNotTakeThePlaceBack() {
        val nudged = start.locating().located(union, 12.0).nudged(Compass.E)
        assertSame(nudged, nudged.located(union, 4.0))
    }

    @Test
    fun aRefusalOrADeviceWithLocationOffIsSaidAndListeningStops() {
        assertEquals(Problem.LocationRefused, start.locating().locationRefused().problem)
        assertEquals(Problem.LocationOff, start.locating().locationOff().problem)
        assertEquals(Locating.IDLE, start.locating().locationOff().locating)
    }

    @Test
    fun pressingTheButtonClearsAnOldComplaint() {
        assertNull(start.locationRefused().locating().problem)
    }

    @Test
    fun aFixOutsideTheEarthIsIgnored() {
        val seeking = start.locating()
        assertSame(seeking, seeking.located(Point(91.0, 0.0), 5.0))
    }

    @Test
    fun runningOutOfPatienceWithNoFixAtAllSaysSo() {
        val stopped = start.locating().stoppedLocating()
        assertEquals(Problem.NoFix, stopped.problem)
        assertEquals(start.selection, stopped.selection)
    }

    @Test
    fun runningOutOfPatienceKeepsTheBestFix() {
        val stopped = start.locating().located(union, 9.0).stoppedLocating()
        assertEquals(Locating.IDLE, stopped.locating)
        assertEquals(9.0, stopped.selection.accuracyMetres)
        assertNull(stopped.problem)
    }
}
