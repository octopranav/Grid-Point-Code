package com.gridpointcode.core

import ca.pranavpatel.algo.gridpointcode.GPC
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What survives a move.
 *
 * The first build of the app carried "Front entrance", written for one market,
 * through a jump to another market, a station and a nudge, and put it in every
 * link it made. These are the rules that stop that, each given the move that
 * broke it.
 */
class PlaceTest {

    private val start = PlaceState(selectionAt(Point(43.650006, -79.380004), Source.SAMPLE))
    private val market = "https://gridpointcode.com/play?c=G3RJM8X6TX&n=Front+entrance"

    @Test
    fun aLinkBringsItsDirections() {
        val state = start.opened(market, Source.LINK)
        assertEquals("G3RJM8X6TX", state.selection.code)
        assertEquals("Front entrance", state.note)
        assertEquals(market, state.view().link)
    }

    @Test
    fun directionsDoNotFollowTheReaderToAnotherPlace() {
        val there = start.opened(market, Source.LINK)
        assertEquals("", there.opened("g3rjl 5frcr", Source.CODE).note)
        assertEquals("", there.opened("geo:43.6453,-79.3806", Source.LINK).note)
        assertEquals("", there.opened("43.6453, -79.3806", Source.CODE).note)
        assertEquals("", there.placed(selectionAt(Point(43.6453, -79.3806), Source.DEVICE)).note)
    }

    @Test
    fun directionsSurviveANudgeBecauseItIsTheSamePlace() {
        val there = start.opened(market, Source.LINK)
        val over = there.nudged(Compass.N)
        assertEquals(Source.NUDGE, over.selection.source)
        assertEquals("Front entrance", over.note)
        assertEquals("https://gridpointcode.com/play?c=${over.selection.code}&n=Front+entrance", over.view().link)
    }

    @Test
    fun aComplaintIsClearedByTheNextPlace() {
        val confused = start.opened("QQQQQYYYYY", Source.CODE)
        assertIs<Problem.Unread>(confused.problem)
        assertNull(confused.opened("G3RJM98NM9", Source.CODE).problem)
        assertNull(confused.nudged(Compass.E).problem)
    }

    @Test
    fun aComplaintLeavesThePlaceWhereItWas() {
        val confused = start.opened("QQQQQYYYYY", Source.CODE)
        assertEquals(start.selection, confused.selection)
    }

    @Test
    fun aShortFormIsReadAgainstWhereTheReaderAlreadyIs() {
        assertEquals("G3RJM98NM9", start.opened("-98NM9", Source.CODE).selection.code)
    }

    @Test
    fun aShortFormNamesTheNearestMatchNotTheOneTheSenderMeant() {
        // Recovery never fails for distance: it names the nearest cell whose
        // last five characters match. From Sydney, "-98NM9" is a doorway in
        // Sydney, not the one in Toronto, which is why the full ten characters
        // are what the app shares and the short form is labelled local only.
        val sydney = PlaceState(selectionAt(Point(-33.8688, 151.2093), Source.SAMPLE))
        val state = sydney.opened("-98NM9", Source.CODE)
        assertNull(state.problem)
        assertEquals("98NM9", state.selection.code.takeLast(5))
        assertTrue(GPC.Distance(sydney.selection.code, state.selection.code) < 10_000.0)
    }

    @Test
    fun symbolsNoCodeUsesAreRefusedInAShortFormToo() {
        val state = start.opened("-QQQQQ", Source.CODE)
        assertIs<Problem.UnreadShort>(state.problem)
        assertEquals(start.selection, state.selection)
    }

    @Test
    fun aReservedCodeIsExplainedAndGoesNowhere() {
        val state = start.opened("XG3RJ98NM9", Source.CODE)
        assertIs<Problem.Reserved>(state.problem)
        assertEquals(start.selection, state.selection)
    }

    @Test
    fun aPlaceFoundByNameIsADifferentDoor() {
        val there = start.opened(market, Source.LINK).locating()
        val found = there.found(Named("Toronto", "G3RJF4318R", "Ontario, Canada"))
        assertEquals("G3RJF4318R", found.selection.code)
        assertEquals(Source.SEARCH, found.selection.source)
        assertEquals("", found.note)
        assertEquals(Locating.IDLE, found.locating)
    }

    @Test
    fun aNameWhoseCodeWillNotReadLeavesThePlaceWhereItWas() {
        val confused = start.found(Named("Nowhere", "QQQQQYYYYY", ""))
        assertEquals(start.selection, confused.selection)
        assertIs<Problem.Unread>(confused.problem)
    }

    @Test
    fun directionsAreTidiedWhenTheyAreWritten() {
        assertEquals("Blue gate, second door", start.described("  Blue gate,\n second door ").note)
    }
}
