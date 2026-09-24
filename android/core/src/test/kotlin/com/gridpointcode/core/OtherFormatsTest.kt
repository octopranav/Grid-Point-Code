package com.gridpointcode.core

import ca.pranavpatel.algo.gridpointcode.GPC
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Locations written in other systems, each held to its owner's published
 * answers: the Plus Code rows are from the format's own decoding, recovery and
 * validity tests, and the DIGIPIN answers are what India Post's reference
 * decoder gives for the same pins.
 */
class OtherFormatsTest {

    private val start = PlaceState(selectionAt(Point(51.5007, -0.1246), Source.SAMPLE))

    private fun at(text: String): OtherFormat.At = assertIs<OtherFormat.At>(readOther(text), text)

    private fun near(expected: Double, actual: Double, tolerance: Double, what: String) =
        assertTrue(abs(expected - actual) <= tolerance, "$what: expected $expected, got $actual")

    @Test
    fun aFullPlusCodeIsItsAreasCentre() {
        // code, south, west, north, east
        val rows = listOf(
            listOf("7FG49Q00+", 20.35, 2.75, 20.4, 2.8),
            listOf("7FG49QCJ+2V", 20.37, 2.782125, 20.370125, 2.78225),
            listOf("7FG49QCJ+2VX", 20.3701, 2.78221875, 20.370125, 2.78225),
            listOf("7FG49QCJ+2VXGJ", 20.370113, 2.782234375, 20.370114, 2.78223632813),
            listOf("8FVC2222+22", 47.0, 8.0, 47.000125, 8.000125),
            listOf("4VCPPQGP+Q9", -41.273125, 174.785875, -41.273, 174.786),
            listOf("62G20000+", 0.0, -180.0, 1.0, -179.0),
            listOf("22222222+22", -90.0, -180.0, -89.999875, -179.999875),
            listOf("6FH32222+222", 1.0, 1.0, 1.000025, 1.00003125),
        )
        for (row in rows) {
            val code = row[0] as String
            val found = at(code)
            assertEquals(Format.PLUS_CODE, found.format)
            near(((row[1] as Double) + (row[3] as Double)) / 2, found.point.latitude, 1e-6, "$code latitude")
            near(((row[2] as Double) + (row[4] as Double)) / 2, found.point.longitude, 1e-6, "$code longitude")
        }
        // A ten-symbol code names about 14 m north to south: its centre is not a door.
        near(13.9, at("8FVC2222+22").metres, 0.1, "size of a ten-symbol code")
    }

    @Test
    fun aShortPlusCodeIsReadAgainstAPlaceNearIt() {
        // full code, latitude, longitude, short code
        val rows = listOf(
            listOf("9C3W9QCJ+2VX", 51.3701125, -1.217765625, "+2VX"),
            listOf("9C3W9QCJ+2VX", 51.3708675, -1.217765625, "CJ+2VX"),
            listOf("9C3W9QCJ+2VX", 51.3693575, -1.217765625, "CJ+2VX"),
            listOf("9C3W9QCJ+2VX", 51.3852125, -1.217765625, "9QCJ+2VX"),
            listOf("9C3W9QCJ+2VX", 51.3701125, -1.232865625, "9QCJ+2VX"),
            listOf("8FJFW222+", 42.899, 9.012, "22+"),
            listOf("796RXG22+", 14.95125, -23.5001, "22+"),
            listOf("8FVC2GGG+GG", 46.976, 8.526, "2GGG+GG"),
            listOf("8FRCXGGG+GG", 47.026, 8.526, "XGGG+GG"),
            listOf("8FR9GXGG+GG", 46.526, 8.026, "GXGG+GG"),
            listOf("8FRCG2GG+GG", 46.526, 7.976, "G2GG+GG"),
            // Near the poles, where recovery must not step off the Earth.
            listOf("CFX22222+22", 89.6, 0.0, "2222+22"),
            listOf("2CXXXXXX+XX", -81.0, 0.0, "XXXXXX+XX"),
        )
        for (row in rows) {
            val full = at(row[0] as String).point
            val short = row[3] as String
            val recovered = recoverNear(short, Point(row[1] as Double, row[2] as Double))!!.point
            near(full.latitude, recovered.latitude, 1e-9, "$short latitude")
            near(full.longitude, recovered.longitude, 1e-9, "$short longitude")
        }
    }

    @Test
    fun onlyAValidPlusCodeIsRead() {
        val valid = listOf("8FWC2345+G6", "8FWC2345+G6G", "8fwc2345+", "8FWCX400+", "84000000+", "WC2345+G6g", "2345+G6", "45+G6", "+G6", "849VGJQF+VX7QR3J", "849VGJQF+VX7QR3JW")
        val invalid = listOf("G+", "+", "8FWC2345+G", "8FWC2_45+G6", "8FWC2\u03b745+G6", "8FWC2345+G6+", "8FWC2345G6+", "8FWC2300+G6", "WC2300+G6g", "WC2345+G", "WC2300+", "84900000+", "849VGJQF+VX7QR3U", "849VGJQF+VX7QR3JU")
        valid.forEach { assertTrue(readOther(it) != null, "$it is valid") }
        invalid.forEach { assertNull(readOther(it), "$it is not") }
    }

    @Test
    fun aShortPlusCodeCarriesItsTown() {
        assertEquals(OtherFormat.Near(Format.PLUS_CODE, "CJ+2VX", "Newbury, UK"), readOther("CJ+2VX Newbury, UK"))
        assertEquals(OtherFormat.Near(Format.PLUS_CODE, "CJ+2VX", null), readOther("cj+2vx"))
    }

    @Test
    fun aDigipinWithItsSeparatorsIsReadAsIndiaPostReadsIt() {
        val dakBhawan = at("39J-49L-L8T4")
        assertEquals(Format.DIGIPIN, dakBhawan.format)
        near(28.622793, dakBhawan.point.latitude, 1e-6, "Dak Bhawan latitude")
        near(77.213049, dakBhawan.point.longitude, 1e-6, "Dak Bhawan longitude")
        val chennai = at("4t3 96f 42l7")
        near(13.111780, chennai.point.latitude, 1e-6, "Chennai latitude")
        near(80.202635, chennai.point.longitude, 1e-6, "Chennai longitude")
        val mumbai = at("DIGIPIN 4FKPC2K97F")
        near(18.939787, mumbai.point.latitude, 1e-6, "Mumbai latitude")
        near(72.835512, mumbai.point.longitude, 1e-6, "Mumbai longitude")
        near(3.8, mumbai.metres, 0.1, "size of a DIGIPIN")
    }

    @Test
    fun tenBareCharactersAreACodeFirstAndADigipinBeside() {
        // Every DIGIPIN symbol is also one of this system's, so a DIGIPIN
        // written as it has been since May 2026 is also a code here.
        assertIs<Reading.Code>(read("4T396F42L7"))
        val instead = otherReading("4T396F42L7")!!
        near(13.111780, instead.point.latitude, 1e-6, "the DIGIPIN reading")
        assertNull(otherReading("G3RJM98NM9"), "G is not a DIGIPIN symbol")
    }

    @Test
    fun aGeohashIsReadOnlyWhenItSaysSo() {
        val short = at("geohash:ezs42")
        assertEquals(42.60498046875, short.point.latitude, 1e-12)
        assertEquals(-5.60302734375, short.point.longitude, 1e-12)
        val long = at("http://geohash.org/u4pruydqqvj")
        near(57.64911063, long.point.latitude, 1e-6, "geohash latitude")
        near(10.40743969, long.point.longitude, 1e-6, "geohash longitude")
        assertNull(readOther("ezs42"), "a bare geohash is a word as often as not")
    }

    @Test
    fun aSharedGooglePlaceIsItsPinNotTheView() {
        val place = at(
            "https://www.google.com/maps/place/CN+Tower/@43.6425662,-79.3892317,17z/data=!3m1!4b1!4m6!3m5!1s0x882b34d68bf33a9b:0x15edd8c4de1c7581!8m2!3d43.6425662!4d-79.3870568!16zL20vMDFfdDk",
        )
        assertEquals(Format.GOOGLE_MAPS, place.format)
        assertEquals(Point(43.6425662, -79.3870568), place.point)
        assertEquals(false, place.viewCentre)
    }

    @Test
    fun aMapLinkIsReadForItsCoordinates() {
        val view = at("https://www.google.com/maps/@43.6532,-79.3832,15z")
        assertEquals(true, view.viewCentre)
        near(11.1, view.metres, 0.1, "four decimals")
        assertEquals(Point(43.6532, -79.3832), at("https://maps.google.com/?q=43.6532,-79.3832").point)
        assertEquals(Point(43.6532, -79.3832), at("google.com/maps/search/43.6532,+-79.3832").point)
        assertEquals(Format.APPLE_MAPS, at("https://maps.apple.com/?ll=43.6532,-79.3832&q=Dropped%20Pin").format)
        val marker = at("https://www.openstreetmap.org/?mlat=43.65320&mlon=-79.38320#map=17/43.65320/-79.38320")
        assertEquals(Format.OPENSTREETMAP, marker.format)
        assertEquals(false, marker.viewCentre)
        assertEquals(true, at("https://www.openstreetmap.org/#map=17/43.6532/-79.3832").viewCentre)
        val bing = at("https://www.bing.com/maps?cp=43.6532~-79.3832&lvl=16")
        assertEquals(Format.BING_MAPS, bing.format)
        assertEquals(true, bing.viewCentre)
        assertEquals(Format.WAZE, at("https://www.waze.com/ul?ll=43.6532,-79.3832&navigate=yes").format)
        assertEquals(Format.PLUS_CODE, at("https://plus.codes/8FVC2222+22").format)
    }

    @Test
    fun aLinkSaysOnlyAsMuchAsItsDecimals() {
        near(1113.2, at("https://maps.google.com/?q=43.65,-79.38").metres, 0.1, "two decimals")
    }

    @Test
    fun whatCannotBeReadIsNamedAndNotGuessed() {
        assertEquals(OtherFormat.Closed(Format.WHAT3WORDS), readOther("///filled.count.soap"))
        assertEquals(OtherFormat.Closed(Format.WHAT3WORDS), readOther("https://w3w.co/filled.count.soap"))
        assertEquals(OtherFormat.Unfollowed(Format.GOOGLE_MAPS), readOther("https://maps.app.goo.gl/AbC123xyz"))
        assertNull(readOther("///notwords"))
    }

    @Test
    fun thisSystemsOwnTextReadsAsBefore() {
        assertIs<Reading.Code>(read("#G3RJM-98NM9"))
        assertIs<Reading.Short>(read("-98NM9"))
        assertIs<Reading.At>(read("43.65, -79.38"))
        assertIs<Reading.Code>(read("https://gridpointcode.com/play?c=G3RJM98NM9"))
    }

    @Test
    fun aConvertedPlaceRemembersWhereItCameFromUntilItMoves() {
        val opened = start.described("Blue gate").opened("https://www.openstreetmap.org/?mlat=43.6532&mlon=-79.3832", Source.CODE)
        assertEquals(Source.CONVERTED, opened.selection.source)
        assertEquals(GPC.Encode(43.6532, -79.3832, false), opened.selection.code)
        assertEquals(Format.OPENSTREETMAP, opened.origin?.format)
        assertEquals("", opened.note, "directions belong to the place they were written for")
        assertNull(opened.nudged(Compass.N).origin, "one cell over is not what the link said")
    }

    @Test
    fun whatCannotBeReadLeavesThePlaceWithTheReason() {
        val closed = start.opened("///filled.count.soap", Source.CODE)
        assertEquals(start.selection, closed.selection)
        assertEquals(Problem.Closed(Format.WHAT3WORDS), closed.problem)
        assertEquals(Problem.Unfollowed(Format.GOOGLE_MAPS), start.opened("https://maps.app.goo.gl/AbC123xyz", Source.CODE).problem)
    }

    @Test
    fun aShortPlusCodeWithNoTownIsReadAgainstTheReadersOwnPlace() {
        val opened = start.opened("CJ+2VX", Source.CODE)
        assertEquals(Source.CONVERTED, opened.selection.source)
        assertTrue(abs(opened.selection.point.latitude - start.selection.point.latitude) <= 0.5)
        assertTrue(abs(opened.selection.point.longitude - start.selection.point.longitude) <= 0.5)
    }

    @Test
    fun aShortPlusCodeWithATownWaitsForTheTownToBeFound() {
        val waiting = start.opened("CJ+2VX Newbury, UK", Source.CODE)
        assertEquals(start.selection, waiting.selection)
        assertIs<Problem.Unplaced>(waiting.problem)
    }

    @Test
    fun aTownIsTheOneItsRegionNamesOrTheLargest() {
        val toronto = listOf(
            Named("Toronto", "G3RJF4318R", "Ontario, Canada"),
            Named("Toronto", "6LKJ5C7TM1", "New South Wales, Australia"),
            Named("Toronto", "G3DFC2005T", "Ohio, United States"),
        )
        assertEquals(toronto[0], townFor("Toronto, ON, Canada", toronto))
        assertEquals(toronto[2], townFor("Toronto, Ohio", toronto))
        assertEquals(toronto[0], townFor("Toronto", toronto), "the largest, listed first")
        assertNull(townFor("Nowhereville", toronto))
    }
}
