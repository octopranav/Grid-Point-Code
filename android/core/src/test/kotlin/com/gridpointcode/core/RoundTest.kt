package com.gridpointcode.core

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A round of saved places: worked out from straight lines, from the reader, uncrossed, and shorter than code order. */
class RoundTest {

    private val toronto = Point(43.650006, -79.380004)

    private fun placeAt(point: Point, label: String = "") = SavedPlace(selectionAt(point, Source.MAP).code, label, "", 0)

    /** Stops a hundred metres or so apart, due north of the start, handed over out of order. */
    private val line = listOf(3, 1, 4, 0, 2).map { placeAt(Point(toronto.latitude + 0.001 * (it + 1), toronto.longitude), "stop $it") }

    @Test
    fun stopsInALineAreWalkedAlongIt() {
        val round = roundOf(line, toronto)
        assertEquals(listOf("stop 0", "stop 1", "stop 2", "stop 3", "stop 4"), round.stops.map { it.place.label })
        assertEquals(round.stops.sumOf { it.leg }, round.metres, 1e-6)
        assertEquals(metresBetween(toronto, round.stops.last().point), round.metres, 1.0, "no step back along the way")
    }

    @Test
    fun withNoStartTheRoundBeginsAtWhicheverEndIsShortest() {
        val labels = roundOf(line, null).stops.map { it.place.label }
        assertTrue(labels == listOf("stop 0", "stop 1", "stop 2", "stop 3", "stop 4") || labels == labels.sortedDescending(), labels.toString())
        assertEquals(0.0, roundOf(line, null).stops.first().leg, "the first stop is where the round starts")
    }

    @Test
    fun theRoundIsShorterThanCodeOrderAndNoTwoLegsCross() {
        val random = Random(7)
        repeat(50) {
            val stops = List(20) {
                placeAt(Point(toronto.latitude + random.nextDouble(-0.02, 0.02), toronto.longitude + random.nextDouble(-0.03, 0.03)))
            }
            val round = roundOf(stops, toronto)
            val byCode = listOf(toronto) + stops.sortedBy { it.code }.map { selectionOf(it.code, Source.SAVED).point }
            val codeOrder = byCode.zipWithNext { a, b -> metresBetween(a, b) }.sum()
            assertTrue(round.metres < codeOrder, "round ${round.metres} against code order $codeOrder")
            val path = listOf(toronto) + round.stops.map { it.point }
            for (i in 0 until path.size - 1) {
                for (j in i + 2 until path.size - 1) {
                    assertTrue(!crosses(path[i], path[i + 1], path[j], path[j + 1]), "legs $i and $j cross")
                }
            }
        }
    }

    @Test
    fun eachPlaceOnceAndAnUnreadableOneLeftOut() {
        val round = roundOf(line + SavedPlace("not a code", "", "", 0), toronto)
        assertEquals(line.map { it.code }.sorted(), round.stops.map { it.place.code }.sorted())
        assertEquals(Round(emptyList(), 0.0), roundOf(emptyList(), toronto))
        assertEquals(roundOf(line, toronto), roundOf(line, toronto), "the same stops give the same round")
    }

    /** Whether two legs cross, drawn flat, which is near enough across a city. */
    private fun crosses(a: Point, b: Point, c: Point, d: Point): Boolean {
        fun side(p: Point, q: Point, r: Point): Double =
            (q.longitude - p.longitude) * (r.latitude - p.latitude) - (q.latitude - p.latitude) * (r.longitude - p.longitude)
        val d1 = side(c, d, a)
        val d2 = side(c, d, b)
        val d3 = side(a, b, c)
        val d4 = side(a, b, d)
        return d1 * d2 < 0 && d3 * d4 < 0 && listOf(d1, d2, d3, d4).all { abs(it) > 1e-12 }
    }
}
