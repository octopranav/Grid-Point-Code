package com.gridpointcode.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What the map draws: the cell and the ones around it, edge to edge. */
class CellTest {

    private val toronto = selectionAt(Point(43.650006, -79.380004), Source.SAMPLE)

    @Test
    fun theCellHasTheEdgesTheSiteDraws() {
        val box = cellBox(toronto.code)
        assertEquals(43.64999424, box.south, 1e-9)
        assertEquals(-79.3800192, box.west, 1e-9)
        assertEquals(43.65001728, box.north, 1e-9)
        assertEquals(-79.37998848, box.east, 1e-9)
    }

    @Test
    fun theCellHoldsThePointThatNamedIt() {
        assertTrue(cellBox(toronto.code).contains(toronto.point))
    }

    @Test
    fun neighboursShareAnEdgeWithNoGapBetween() {
        val cell = cellBox(toronto.code)
        val cells = around(toronto.code)
        val north = cellBox(cells.getValue(Compass.N))
        val east = cellBox(cells.getValue(Compass.E))
        assertEquals(cell.north, north.south, 1e-12)
        assertEquals(cell.east, east.west, 1e-12)
    }

    @Test
    fun thereAreEightNeighboursAwayFromThePoles() {
        assertEquals(8, neighbourBoxes(toronto.code).size)
    }
}
