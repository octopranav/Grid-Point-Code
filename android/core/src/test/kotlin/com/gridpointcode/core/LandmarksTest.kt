package com.gridpointcode.core

import ca.pranavpatel.algo.gridpointcode.GPC
import kotlin.math.PI
import kotlin.math.cos
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Which landmarks a short form may be given with.
 *
 * The Toronto landmarks are the live archive's own, from the shard around the
 * specification's example point, and the distances, bearings and fractions of
 * the box expected for them are what the website's reference.ts gives for the
 * same nine. The rest is measured against the library itself: a reference the
 * rule admits must recover the code, and one it refuses must not.
 */
class LandmarksTest {

    /** The specification's example, `#G3RJM-98NM9`. */
    private val example = Point(43.650006, -79.380004)

    private fun place(name: String, latitude: Double, longitude: Double, kind: LandmarkKind = LandmarkKind.PLACE) =
        Landmark(name, latitude, longitude, "Ontario, Canada", kind)

    private val toronto = listOf(
        place("Old Toronto", 43.64999, -79.38206),
        place("Downtown Toronto", 43.65011, -79.3829),
        place("Toronto Old City Hall", 43.65249, -79.38201, LandmarkKind.STRUCTURE),
        place("Financial District", 43.64713, -79.38094),
        place("Nathan Phillips Square", 43.6523, -79.38345, LandmarkKind.STRUCTURE),
        place("Hockey Hall of Fame", 43.64695, -79.37743, LandmarkKind.STRUCTURE),
        // Just outside the box, each by a different axis or both.
        place("Bennington Heights", 43.69417, -79.37222),
        place("Broadview North", 43.68883, -79.35563),
        place("Brockton Village", 43.65272, -79.43368),
    )

    private fun code(point: Point): String = GPC.Encode(point.latitude, point.longitude, false)

    private fun recovered(code: String, reference: Point): String =
        GPC.Normalise(GPC.RecoverShort(GPC.Shorten(code), reference.latitude, reference.longitude))[0]

    @Test
    fun theBoxIsTheOneTheSpecificationPrints() {
        assertEquals(0.03598848, Recovery.latitude, 1e-12)
        assertEquals(0.04798464, Recovery.longitude, 1e-12)
    }

    @Test
    fun theAnchorsAreTheOnesTheSiteLists() {
        val found = anchorsFor(example, toronto)
        assertEquals(
            listOf("Old Toronto", "Downtown Toronto", "Toronto Old City Hall", "Financial District", "Nathan Phillips Square", "Hockey Hall of Fame"),
            found.map { it.landmark.name },
        )
        assertEquals(listOf("W", "W", "NW", "S", "NW", "SE"), found.map { it.bearing })
        listOf(165.430, 233.291, 319.905, 328.545, 376.742, 397.949)
            .zip(found) { metres, anchor -> assertEquals(metres, anchor.metres, 0.001, anchor.landmark.name) }
        listOf(0.042847, 0.060353, 0.069022, 0.079914, 0.071815, 0.084916)
            .zip(found) { tight, anchor -> assertEquals(tight, anchor.tightness, 0.000001, anchor.landmark.name) }
    }

    @Test
    fun everyReferenceInsideTheBoxRecoversTheCode() {
        val random = Random(12)
        var tried = 0
        repeat(3000) {
            val point = Point(random.nextDouble(-80.0, 80.0), random.nextDouble(-179.0, 179.0))
            val code = runCatching { code(point) }.getOrNull() ?: return@repeat
            val reference = Point(
                point.latitude + random.nextDouble(-1.0, 1.0) * Recovery.latitude,
                point.longitude + random.nextDouble(-1.0, 1.0) * Recovery.longitude,
            )
            assertTrue(tightness(point, reference.latitude, reference.longitude) <= 1)
            assertEquals(code, recovered(code, reference), "$point from $reference")
            tried += 1
        }
        assertTrue(tried > 2900, "only $tried points had a code")
    }

    @Test
    fun aReferenceJustOutsideTheBoxRecoversSomewhereElse() {
        // Nothing fails out there. Recovery answers, with a neighbouring cell's
        // copy of the same five characters, which is why the box is a rule and
        // not a preference.
        val random = Random(34)
        var tried = 0
        var wrong = 0
        repeat(2000) {
            val point = Point(random.nextDouble(-70.0, 70.0), random.nextDouble(-170.0, 170.0))
            val code = runCatching { code(point) }.getOrNull() ?: return@repeat
            val over = random.nextDouble(1.01, 1.2) * (if (random.nextBoolean()) 1 else -1)
            val within = random.nextDouble(-0.9, 0.9)
            val reference = if (random.nextBoolean()) {
                Point(point.latitude + over * Recovery.latitude, point.longitude + within * Recovery.longitude)
            } else {
                Point(point.latitude + within * Recovery.latitude, point.longitude + over * Recovery.longitude)
            }
            assertTrue(tightness(point, reference.latitude, reference.longitude) > 1)
            tried += 1
            if (recovered(code, reference) != code) wrong += 1
        }
        assertEquals(tried, wrong, "every reference outside the box recovered somewhere else")
    }

    @Test
    fun theBoxIsDegreesNotMetres() {
        fun east(point: Point, metres: Double) =
            Point(point.latitude, point.longitude + metres / (111_195.0 * cos(point.latitude * PI / 180)))

        val equator = Point(0.5, 20.0)
        val helsinki = Point(60.17, 24.94)
        val nearEquator = east(equator, 3000.0)
        val nearHelsinki = east(helsinki, 3000.0)

        assertEquals(1, anchorsFor(equator, listOf(place("East", nearEquator.latitude, nearEquator.longitude))).size)
        assertEquals(0, anchorsFor(helsinki, listOf(place("East", nearHelsinki.latitude, nearHelsinki.longitude))).size)
        // And the one left out really would have named somewhere else.
        assertNotEquals(code(helsinki), recovered(code(helsinki), nearHelsinki))
    }

    @Test
    fun theNearerComesFirstEvenWhenItUsesMoreOfTheBox() {
        val here = Point(0.2, 10.2)
        val north = place("North", 0.2 + 3500 / 111_195.0, 10.2)
        val east = place("East", 0.2, 10.2 + 3800 / (111_195.0 * cos(0.2 * PI / 180)))
        val found = anchorsFor(here, listOf(east, north))
        assertEquals(listOf("North", "East"), found.map { it.landmark.name })
        assertTrue(found[0].tightness > found[1].tightness)
    }

    @Test
    fun exactMeansTheSameLevelFiveCell() {
        assertTrue(anchorsFor(example, toronto).all { it.exact }, "all six share G3RJM")
        // Inside the box and across into a neighbouring level-5 cell: still a
        // reference, and not an exact one.
        assertEquals("G3RJM", GPC.Cell(code(example), 5))
        val across = listOf(0.035 to 0.0, -0.035 to 0.0, 0.0 to 0.047, 0.0 to -0.047)
            .map { (dLat, dLng) -> place("Across", example.latitude + dLat, example.longitude + dLng) }
            .first { GPC.Cell(GPC.Encode(it.latitude, it.longitude, false), 5) != "G3RJM" }
        val found = anchorsFor(example, listOf(across)).single()
        assertEquals(false, found.exact)
    }

    @Test
    fun everyPlaceInTheBoxLivesInOneOfItsShards() {
        assertEquals(listOf("G3RJ"), shardsFor(example, 4))
        val random = Random(56)
        repeat(4000) {
            val point = Point(random.nextDouble(-80.0, 80.0), random.nextDouble(-179.0, 179.0))
            val landmark = Point(
                point.latitude + random.nextDouble(-1.0, 1.0) * Recovery.latitude,
                point.longitude + random.nextDouble(-1.0, 1.0) * Recovery.longitude,
            )
            val home = runCatching { GPC.Cell(code(landmark), 4) }.getOrNull() ?: return@repeat
            val shards = shardsFor(point, 4)
            assertTrue(shards.size in 1..4, "$point reaches ${shards.size} shards")
            assertTrue(home in shards, "$landmark lives in $home, outside $shards")
        }
    }

    @Test
    fun theLineIsTheShortFormThenThePlace() {
        val line = anchored(code(example), toronto[0])
        assertEquals("-98NM9 near Old Toronto, Ontario, Canada", line)
        // And it reads back: the five characters against that landmark are the code.
        assertEquals(code(example), recovered(code(example), Point(43.64999, -79.38206)))
    }
}
