package com.gridpointcode.core

import ca.pranavpatel.algo.gridpointcode.GPC
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Areas: a code's first few characters, read, drawn, shared and spoken. */
class AreasTest {

    private fun round(degrees: Double) = Math.round(degrees * 1e9) / 1e9

    /** The specification's example, `#G3RJM-98NM9`, in the level-5 cell `G3RJM`. */
    private val example = PlaceState(selectionAt(Point(43.650006, -79.380004), Source.SAMPLE))

    @Test
    fun aCellIsTheAreaHoldingEveryCodeThatBeginsWithIt() {
        val district = areaOf("G3RJM")!!
        assertEquals(5, district.level)
        assertEquals(0.072, district.box.north - district.box.south, 1e-9)
        assertEquals(0.096, district.box.east - district.box.west, 1e-9)
        assertTrue(district.box.contains(example.selection.point))
        // The edges the website draws for the same cell (web/scripts/test-areas.mjs).
        assertEquals(Box(43.632, -79.392, 43.704, -79.296), district.box.let {
            Box(round(it.south), round(it.west), round(it.north), round(it.east))
        })
        val random = Random(9)
        repeat(500) {
            val inside = Point(
                random.nextDouble(district.box.south, district.box.north),
                random.nextDouble(district.box.west, district.box.east),
            )
            assertTrue(GPC.Encode(inside.latitude, inside.longitude, false).startsWith("G3RJM"), "$inside")
        }
        val outside = Point(district.box.north + 0.001, district.centre.longitude)
        assertTrue(!GPC.Encode(outside.latitude, outside.longitude, false).startsWith("G3RJM"))
    }

    @Test
    fun smallerAreasLieInsideLargerOnes() {
        val region = areaOf("G3R")!!.box
        val district = areaOf("G3RJM")!!.box
        assertTrue(district.south >= region.south && district.north <= region.north)
        assertTrue(district.west >= region.west && district.east <= region.east)
    }

    @Test
    fun aCellIsReadAsACodeIsRead() {
        assertEquals("G3RJM", areaOf("g3rjm")?.cell)
        assertEquals("G3RJM0", areaOf("G3RJMO")?.cell, "a confusable letter reads as the symbol it stands for")
    }

    @Test
    fun onlyACellIsAnArea() {
        assertNull(areaOf("G3RJM98NM9"), "ten characters is a code")
        assertNull(areaOf("#G3RJM"), "the hash marks a code")
        assertNull(areaOf("XG3"), "the reserved range names nowhere")
        assertNull(areaOf("G3RJQ"), "Q is not in the alphabet, nor read as anything that is")
        assertNull(areaOf(""))
    }

    @Test
    fun typedTextIsAnAreaWhenItLooksLikeOne() {
        assertEquals("G3RJM", assertIs<Reading.Area>(read("G3RJM")).area.cell)
        assertIs<Reading.Unread>(read("CNN"), "letters alone are a word as often as not")
        // The format's own table folds E to 3 and O to 0, so a word can be a
        // cell; typed, it is still looked up as a word first.
        assertEquals("H3LL0", areaOf("HELLO")?.cell)
        assertIs<Reading.Unread>(read("hello"))
        assertIs<Reading.Unread>(read("10001"), "digits alone are a postcode as often as not")
        assertIs<Reading.Code>(read("G3RJM98NM9"))
        assertIs<Reading.Short>(read("-98NM9"))
    }

    @Test
    fun aLinkToAnAreaIsAnAreaWhateverItsSymbols() {
        assertEquals("CNN", assertIs<Reading.Area>(read("https://gridpointcode.com/play?c=CNN")).area.cell)
        assertEquals("https://gridpointcode.com/play?c=G3RJM", areaAddress("G3RJM"))
        assertEquals("G3RJM", assertIs<Reading.Area>(read(areaAddress("G3RJM"))).area.cell)
    }

    @Test
    fun anAreaIsSpokenSymbolBySymbolWithNoCheckWord() {
        assertEquals("Golf, three, Romeo, Juliett, Mike", aloudArea("G3RJM"))
    }

    @Test
    fun widenedShowsTheAreaAroundThePlaceAndNarrowedComesBack() {
        val wide = example.widened(5)
        assertEquals("G3RJM", wide.area?.cell)
        assertEquals(example.selection, wide.selection, "the place stays underneath")
        assertEquals(example.selection, wide.narrowed().selection)
        assertNull(wide.narrowed().area)
        assertNull(wide.nudged(Compass.N).area, "a nudge is about the door, not the area")
    }

    @Test
    fun anAreaFromALinkIsItsCentreWithTheAreaOverIt() {
        val opened = example.described("Blue gate").opened(areaAddress("G3RJM"), Source.LINK)
        assertEquals(Source.AREA, opened.selection.source)
        assertEquals("G3RJM", opened.area?.cell)
        assertTrue(opened.area!!.box.contains(opened.selection.point))
        assertEquals("", opened.note)
    }

    @Test
    fun theViewOffersEveryAreaFromARegionToABuilding() {
        val view = example.view()
        assertEquals(listOf("G3RJM98NM", "G3RJM98N", "G3RJM98", "G3RJM9", "G3RJM", "G3RJ", "G3R"), view.areas.map { it.cell })
        assertEquals(8001.5, view.areas.first { it.level == 5 }.size.northSouthMetres, 0.1)
        assertEquals("https://gridpointcode.com/play?c=G3RJM", view.areas.first { it.level == 5 }.link)
        assertNull(view.area)
        assertEquals("G3RJM", example.widened(5).view().area?.cell)
    }
}
