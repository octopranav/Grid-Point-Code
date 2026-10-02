package com.gridpointcode.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What a reader left on screen is what they come back to, after Android has
 * ended the app's process in the background to have its memory.
 */
class SurviveTest {

    private val start = PlaceState(selectionAt(Point(43.650006, -79.380004), Source.SAMPLE))

    /** Through the strings the platform keeps, and back. */
    private fun PlaceState.afterProcessDeath(): PlaceState? {
        val kept = toSaved()
        return restoredPlace { kept[it] }
    }

    @Test
    fun aTypedCodeComesBackWithItsDirections() {
        val market = start.opened("https://gridpointcode.com/play?c=G3RJM8X3L1&n=North+door", Source.LINK)
        val back = market.afterProcessDeath()!!
        assertEquals(market.selection, back.selection)
        assertEquals("North door", back.note)
    }

    @Test
    fun aFixComesBackAsCloseAsItWas() {
        val fix = start.locating().located(Point(43.64546, -79.38063), 4.0)
        val back = fix.afterProcessDeath()!!
        assertEquals(fix.selection, back.selection)
        assertEquals(4.0, back.selection.accuracyMetres)
    }

    @Test
    fun anAreaShownInsteadOfThePlaceComesBackOverIt() {
        val widened = start.opened("G3RJM8X3L1", Source.CODE).widened(5)
        val back = widened.afterProcessDeath()!!
        assertEquals(widened.area, back.area)
        assertEquals(widened.selection, back.selection)
    }

    @Test
    fun aConvertedPlaceStillSaysWhereItCameFrom() {
        val origin = Origin(Format.PLUS_CODE, "87M2MJ5C+2M", 13.9, viewCentre = false)
        val converted = start.copy(selection = selectionAt(Point(43.6475, -79.3784), Source.CONVERTED), origin = origin)
        assertEquals(origin, converted.afterProcessDeath()!!.origin)
    }

    @Test
    fun whatBelongsToTheMomentDoesNotComeBack() {
        val doubted = start.opened("G3RJL5FRCR", Source.CODE).opened("G3RJM8X3L1", Source.CODE).locating()
        val back = doubted.afterProcessDeath()!!
        assertNull(back.doubt, "the offer of slips")
        assertEquals(Locating.IDLE, back.locating, "the listening for a fix")
        assertNull(start.opened("QQQQQYYYYY", Source.CODE).afterProcessDeath()!!.problem, "a complaint")
    }

    @Test
    fun nothingKeptOrNothingReadableIsAFreshStart() {
        assertNull(restoredPlace { null })
        assertNull(restoredPlace { if (it == "place.source") "RENAMED" else "43.6" })
    }
}
