package com.gridpointcode.core

import ca.pranavpatel.algo.gridpointcode.GPC
import java.util.Locale
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The app must say exactly what the website says about the same place.
 *
 * Every expected value here is one the site already shows for the Toronto code
 * the specification uses as its example, or one the specification prints. When
 * the two disagree, the app is the one that is wrong.
 */
class CoreTest {

    private val toronto = selectionAt(Point(43.650006, -79.380004), Source.SAMPLE)

    @Test
    fun theSampleEncodesToTheSpecificationsCode() {
        assertEquals("G3RJM98NM9", toronto.code)
        assertEquals("#G3RJM-98NM9", formatted(toronto.code))
        assertEquals("-98NM9", shortForm(toronto.code))
    }

    @Test
    fun everyWrittenFormMatchesTheSite() {
        val rows = forms(toronto, Locale.US).associate { it.key to it.value }
        assertEquals("-98NM9", rows[FormKey.SHORT])
        assertEquals("#G3RJM-98NM9*T", rows[FormKey.CHECK])
        assertEquals("50,180,843,496,709", rows[FormKey.INTEGER])
        assertEquals("43°39'00.02\"N, 79°22'48.01\"W", rows[FormKey.DMS])
        assertEquals("geo:43.650006,-79.380004", rows[FormKey.GEO_URI])
    }

    @Test
    fun theIntegerIsGroupedTheWayTheReadersLanguageGroupsIt() {
        val rows = forms(toronto, Locale.GERMANY).associate { it.key to it.value }
        assertEquals("50.180.843.496.709", rows[FormKey.INTEGER])
    }

    @Test
    fun readAloudIsTheSpecificationsWorkedExample() {
        assertEquals(
            "Golf, three, Romeo, Juliett, Mike; nine, eight, November, Mike, nine; check Tango",
            aloud(toronto.code),
        )
    }

    @Test
    fun readAloudAddsTheCheckCharacterOnlyOnce() {
        assertEquals(aloud("G3RJM98NM9"), aloud("#G3RJM-98NM9*T"))
    }

    @Test
    fun readAloudTakesAnotherLanguagesWords() {
        val words = INTERNATIONAL.copy(letters = RADIO_ALPHABET.mapValues { (symbol, _) -> "<$symbol>" }, check = "control")
        assertTrue(aloud(toronto.code, words).startsWith("<G>, three, <R>"))
        assertTrue(aloud(toronto.code, words).endsWith("; control <T>"))
    }

    @Test
    fun theDistanceBetweenTwoPointsIsMeasuredAlongTheGround() {
        assertEquals(111_195.08, metresBetween(Point(0.0, 0.0), Point(1.0, 0.0)), 0.01, "a degree of latitude")
        assertEquals(0.0, metresBetween(toronto.point, toronto.point), 1e-9)
        assertEquals(
            metresBetween(Point(0.0, 0.0), Point(0.0, 0.2)),
            metresBetween(Point(0.0, 179.9), Point(0.0, -179.9)),
            1e-6,
            "across the antimeridian, the short way",
        )
    }

    @Test
    fun thePrivacyPageIsTheSitesAndNotOneTheAppClaims() {
        // The app opens links under /play itself; the privacy page must go to the browser.
        assertEquals("https://gridpointcode.com/privacy", PRIVACY_ADDRESS)
        assertTrue(!PRIVACY_ADDRESS.startsWith(PLACE_ADDRESS))
    }

    @Test
    fun aLinkCarriesTheCodeAndItsDirections() {
        assertEquals(
            "https://gridpointcode.com/play?c=G3RJM98NM9&n=Blue+gate%2C+second+door+on+the+left",
            addressOf("#G3RJM-98NM9", "Blue gate, second door on the left"),
        )
        assertEquals("https://gridpointcode.com/play?c=G3RJM98NM9", addressOf("G3RJM98NM9"))
    }

    @Test
    fun directionsAreOneTidyLineOfAtMostEightyCharacters() {
        assertEquals("Blue gate, second door", tidyNote("  Blue gate,\n  second   door "))
        assertEquals(NOTE_LIMIT, tidyNote("x".repeat(200)).length)
    }

    @Test
    fun aLinkIsReadBackToItsCodeAndDirections() {
        val link = addressOf("G3RJM98NM9", "Blue gate, second door on the left")
        assertEquals("G3RJM98NM9", codeIn(link))
        assertEquals("Blue gate, second door on the left", noteIn(link))
    }

    @Test
    fun theReaderTakesACodeHoweverItIsWritten() {
        assertEquals(Reading.Code("G3RJM98NM9"), read("g3rjm 98nm9"))
        assertEquals(Reading.Code("G3RJM98NM9", checked = true), read("#G3RJM-98NM9*T"), "and knows a check character held")
    }

    @Test
    fun theReaderTakesALink() {
        val reading = read("https://gridpointcode.com/play?c=G3RJM98NM9&n=Blue+gate")
        assertEquals(Reading.Code("G3RJM98NM9", "Blue gate"), reading)
    }

    @Test
    fun theReaderTakesCoordinatesInThreeSpellings() {
        assertIs<Reading.At>(read("43.650006, -79.380004"))
        assertIs<Reading.At>(read("geo:43.650006,-79.380004"))
        assertIs<Reading.At>(read("43°39'00.02\"N, 79°22'48.01\"W"))
    }

    @Test
    fun theReaderRefusesCoordinatesOutsideTheEarth() {
        assertIs<Reading.Unread>(read("91.0, 10.0"))
    }

    @Test
    fun theReaderHoldsAShortFormUntilItHasAReference() {
        assertEquals(Reading.Short("-98NM9"), read("-98NM9"))
        val near = Point(43.66, -79.39)
        assertEquals("G3RJM98NM9", recover("-98NM9", near)?.code)
    }

    @Test
    fun aReservedCodeIsReportedAsReservedNotWrong() {
        assertIs<Reading.Reserved>(read("XG3RJ98NM9"))
    }

    @Test
    fun lettersTheAliasTableRefusesAreUnread() {
        // Not NOTACODE12: the alias table reads O, A and E as digits, and that
        // string is a real place in Asia.
        assertIs<Reading.Unread>(read("QQQQQYYYYY"))
    }

    @Test
    fun theNeighboursComeBackClockwiseFromNorth() {
        val centre = GPC.Decode(toronto.code)
        val cells = around(toronto.code)
        assertEquals(8, cells.size)
        assertEquals("G3RJM98NMC", cells[Compass.N])

        for ((direction, code) in cells) {
            val other = GPC.Decode(code)
            val north = other.Latitude - centre.Latitude
            val east = other.Longitude - centre.Longitude
            val expectNorth = direction.name.startsWith("N")
            val expectSouth = direction.name.startsWith("S")
            val expectEast = direction.name.endsWith("E")
            val expectWest = direction.name.endsWith("W")
            assertEquals(expectNorth, north > 1e-9, "$direction north")
            assertEquals(expectSouth, north < -1e-9, "$direction south")
            assertEquals(expectEast, east > 1e-9, "$direction east")
            assertEquals(expectWest, east < -1e-9, "$direction west")
        }
    }

    @Test
    fun aCellInTorontoIsTwoAndAHalfMetresEachWay() {
        val size = cellSize(toronto.point.latitude)
        assertTrue(abs(size.northSouthMetres - 2.56) < 0.01)
        assertTrue(abs(size.eastWestMetres - 2.47) < 0.01)
    }

    @Test
    fun aFixIsJudgedAgainstTheSameThreeMetresAsTheSite() {
        val close = toronto.copy(source = Source.DEVICE, accuracyMetres = 2.6)
        val coarse = toronto.copy(source = Source.DEVICE, accuracyMetres = 6.0)
        assertEquals(Fix(3, insideOneCell = true), fixOf(close))
        assertEquals(Fix(6, insideOneCell = false), fixOf(coarse))
    }

    @Test
    fun aPointThatDidNotComeFromADeviceHasNoFixToShow() {
        assertNull(fixOf(toronto.copy(accuracyMetres = 6.0)))
    }

    @Test
    fun aCodeIsPlacedAtTheCentreOfItsCell() {
        val placed = selectionOf("#G3RJM-98NM9", Source.CODE)
        assertEquals("G3RJM98NM9", placed.code)
        assertEquals(43.650006, placed.point.latitude, 1e-6)
        assertEquals(-79.380004, placed.point.longitude, 1e-6)
    }
}
