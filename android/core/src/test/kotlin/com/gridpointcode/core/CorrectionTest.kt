package com.gridpointcode.core

import ca.pranavpatel.algo.gridpointcode.GPC
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A typed code that lands far away, and the codes one slip away that land near the reader. */
class CorrectionTest {

    /** The specification's example, `#G3RJM-98NM9`, as a place the reader had chosen on the map. */
    private val here = selectionOf("G3RJM98NM9", Source.MAP)
    private val state = PlaceState(here)

    @Test
    fun aWrongCharacterInTheDangerousMiddleIsOfferedItsCorrection() {
        // Position 5, M heard as N: a real place, a district away.
        val doubt = assertNotNull(doubtAbout("G3RJN98NM9", here.point, fromDevice = false))
        assertEquals("G3RJN98NM9", doubt.typed)
        assertTrue(doubt.metres > 5_000, "the typed code lands a district away: ${doubt.metres}")
        val first = doubt.suggestions.first()
        assertEquals("G3RJM98NM9", first.code)
        assertEquals(Slip.Replaced(5, 'N'), first.slip)
        assertEquals(0.0, first.metres, 1e-6)
        assertEquals(249, doubt.checked)
    }

    @Test
    fun twoNeighboursSwappedAreOfferedTheirOrder() {
        val doubt = assertNotNull(doubtAbout("G3RMJ98NM9", here.point, fromDevice = true))
        assertEquals(Slip.Swapped(4), doubt.suggestions.first { it.code == "G3RJM98NM9" }.slip)
        assertTrue(doubt.fromDevice)
    }

    @Test
    fun theSuggestionsAreTheLibrarysInItsOrder() {
        val doubt = assertNotNull(doubtAbout("G3RJN98NM9", here.point, fromDevice = false))
        val library = GPC.SuggestCorrections("G3RJN98NM9", here.point.latitude, here.point.longitude, CORRECTION_LEVEL, false)
        assertEquals(library.take(doubt.suggestions.size), doubt.suggestions.map { it.code })
        assertTrue(doubt.suggestions.size <= 3)
    }

    @Test
    fun aCodeThatLandsNearTheReaderIsNotDoubted() {
        assertNull(doubtAbout("G3RJM98NMC", here.point, fromDevice = false), "one cell north")
        assertNull(doubtAbout("G3RJM98NM9", here.point, fromDevice = false), "the reader's own place")
    }

    @Test
    fun repeatedNeighboursAreCountedOnceNotPadded() {
        // Section 15.3's own example: swapping equal neighbours changes nothing.
        assertEquals(242, candidatesFor("P4444PPPPP"))
        assertEquals(249, candidatesFor("G3RJM98NM9"))
    }

    @Test
    fun aTypedCodeCarriesTheDoubtButALinkOrACheckedCodeDoesNot() {
        val typed = state.opened("G3RJN98NM9", Source.CODE)
        assertEquals("G3RJN98NM9", typed.selection.code, "the typed code is opened, as typed")
        assertEquals("G3RJM98NM9", typed.doubt?.suggestions?.first()?.code)

        assertNull(state.opened("https://gridpointcode.com/play?c=G3RJN98NM9", Source.LINK).doubt, "a link was not heard")
        val withCheck = GPC.WithCheck("G3RJN98NM9")
        assertNull(state.opened(withCheck, Source.CODE).doubt, "a check character that holds rules a slip out")
    }

    @Test
    fun theOpeningExampleIsNobodysReference() {
        val opening = PlaceState(selectionOf("G3RJM98NM9", Source.SAMPLE))
        assertNull(opening.opened("G3RJN98NM9", Source.CODE).doubt)
    }

    @Test
    fun choosingASuggestionPlacesItAndKeepingSetsTheDoubtAside() {
        val typed = state.opened("G3RJN98NM9", Source.CODE)
        val chosen = typed.corrected("G3RJM98NM9")
        assertEquals("G3RJM98NM9", chosen.selection.code)
        assertNull(chosen.doubt)
        val kept = typed.kept()
        assertEquals("G3RJN98NM9", kept.selection.code)
        assertNull(kept.doubt)
        assertNull(typed.nudged(Compass.N).doubt, "a nudge accepts the code it refines")
        assertNotNull(typed.view().doubt)
    }

    @Test
    fun aSlipIsOneChangeOrOneSwapAndNothingElse() {
        assertIs<Slip.Replaced>(slipBetween("G3RJN98NM9", "G3RJM98NM9"))
        assertEquals(Slip.Swapped(4), slipBetween("G3RMJ98NM9", "G3RJM98NM9"))
        assertNull(slipBetween("G3RJN98NM9", "G3RJM98NMC"), "two changes are not one slip")
    }
}
