package com.gridpointcode.core

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** What the emergency card shows, and what it refuses to show. */
class EmergencyTest {

    /** A place the reader picked on the map: not where they are. */
    private val picked = PlaceState(selectionAt(Point(51.5007, -0.1246), Source.MAP))

    private val here = Point(43.650006, -79.380004)

    @Test
    fun aFixIsShownInEveryFormAnybodyCanUse() {
        val fixed = picked.locating().located(here, 2.0)
        val card = assertIs<Emergency.Here>(emergencyOf(fixed.view()))
        assertEquals("#G3RJM-98NM9", card.formatted)
        assertEquals("Golf, three, Romeo, Juliett, Mike; nine, eight, November, Mike, nine; check Tango", card.spoken)
        assertEquals("43.650006, -79.380004", card.decimal)
        assertEquals(forms(fixed.selection).first { it.key == FormKey.DMS }.value, card.degrees)
        assertEquals(2, card.metres)
        assertEquals(true, card.insideOneCell)
        assertEquals(false, card.refining, "inside one cell, listening has stopped")
    }

    @Test
    fun aLooseFixSaysItIsStillNarrowing() {
        val card = assertIs<Emergency.Here>(emergencyOf(picked.locating().located(here, 18.0).view()))
        assertEquals(18, card.metres)
        assertEquals(false, card.insideOneCell)
        assertEquals(true, card.refining)
    }

    @Test
    fun theCoordinatesAlwaysUseAFullStop() {
        // A decimal comma between two numbers already separated by one is a trap.
        val card = assertIs<Emergency.Here>(emergencyOf(picked.locating().located(here, 2.0).view(Locale.GERMANY)))
        assertEquals("43.650006, -79.380004", card.decimal)
    }

    @Test
    fun aPlaceThatDidNotComeFromTheDeviceIsNeverShownAsWhereIAm() {
        assertEquals(Emergency.Finding, emergencyOf(picked.view()))
        assertEquals(Emergency.Finding, emergencyOf(picked.opened("G3RJM98NM9", Source.CODE).view()))
    }

    @Test
    fun anOldFixIsNotShownWhileANewOneIsSought() {
        val old = picked.locating().located(here, 2.0)
        assertEquals(Emergency.Finding, emergencyOf(old.locating().view()))
    }

    @Test
    fun whyThereIsNoFixIsSaid() {
        assertEquals(Emergency.Refused, emergencyOf(picked.locationRefused().view()))
        assertEquals(Emergency.Off, emergencyOf(picked.locationOff().view()))
        assertEquals(Emergency.NoFix, emergencyOf(picked.locating().stoppedLocating().view()))
    }
}
