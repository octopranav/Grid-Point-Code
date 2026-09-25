package com.gridpointcode.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The saved list, nearest first from where the reader is, or in the order kept. */
class SavedRowsTest {

    private val here = selectionOf("G3RJM98NM9", Source.DEVICE).point

    private fun kept(code: String, at: Long) = SavedPlace(code, label = code, savedAt = at)

    // Kept most recent first: far, then near, then middling.
    private val saved = listOf(
        kept("G3RJN98NM9", 3),
        kept("G3RJM98NMC", 2),
        kept("G3RJM98N00", 1),
    )

    @Test
    fun nearestFirstOrdersByDistanceFromTheReader() {
        val rows = savedRows(saved, here, SavedOrder.NEAREST)
        assertEquals(listOf("G3RJM98NMC", "G3RJM98N00", "G3RJN98NM9"), rows.map { it.place.code })
        assertTrue(rows.zipWithNext().all { (a, b) -> a.metres!! <= b.metres!! })
    }

    @Test
    fun mostRecentFirstKeepsTheOrderTheyAreKeptIn() {
        assertEquals(saved, savedRows(saved, here, SavedOrder.RECENT).map { it.place })
    }

    @Test
    fun eachRowSaysHowFarAndWhichWay() {
        val north = savedRows(listOf(kept("G3RJM98NMC", 1)), here, SavedOrder.NEAREST).single()
        assertEquals("N", north.octant)
        assertEquals(0.0, north.heading!!, 1.0)
        assertTrue(north.metres!! in 1.0..5.0, "one cell north: ${north.metres}")
        val east = savedRows(listOf(kept("G3RJN98NM9", 1)), here, SavedOrder.NEAREST).single()
        assertEquals("E", east.octant)
        assertEquals(90.0, east.heading!!, 10.0)
    }

    @Test
    fun withNowhereToMeasureFromTheyStayInTheOrderKept() {
        val rows = savedRows(saved, null, SavedOrder.NEAREST)
        assertEquals(saved, rows.map { it.place })
        assertTrue(rows.all { it.metres == null && it.octant == null && it.heading == null })
    }

    @Test
    fun theHeadingIsTheBearingsDegrees() {
        val to = selectionOf("G3RJM98N00", Source.CODE).point
        val octants = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        assertEquals(bearingBetween(here, to), octants[((headingBetween(here, to) / 45).let { Math.round(it) } % 8).toInt()])
        assertNull(savedRows(emptyList(), here, SavedOrder.NEAREST).firstOrNull())
    }
}
