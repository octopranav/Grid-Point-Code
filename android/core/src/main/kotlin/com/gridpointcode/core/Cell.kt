package com.gridpointcode.core

import ca.pranavpatel.algo.gridpointcode.GPC
import kotlin.math.cos
import kotlin.math.roundToInt

/** How big a cell is where it lies, in metres. */
data class CellSize(val northSouthMetres: Double, val eastWestMetres: Double)

/**
 * The size of a cell at a latitude.
 *
 * North to south never changes. East to west narrows with the cosine of the
 * latitude, which is why a cell is square at about 41.4 degrees and a sliver
 * near the poles.
 */
fun cellSize(latitude: Double, level: Int = 10): CellSize {
    val dimensions = GPC.CellDimensions(level)
    return CellSize(
        northSouthMetres = dimensions.NorthSouth,
        eastWestMetres = dimensions.EastWest * cos(Math.toRadians(latitude)),
    )
}

/** A cell's edges, in degrees. */
data class Box(val south: Double, val west: Double, val north: Double, val east: Double) {
    fun contains(point: Point): Boolean =
        point.latitude in south..north && point.longitude in west..east
}

/**
 * The cell a code names, with its edges as the library computes them.
 *
 * The map draws this rectangle rather than a pin, because a pin implies a point
 * and a code names an area: two and a half metres each way, which is a doorway
 * and not a spot on it.
 */
fun cellBox(code: String): Box = GPC.DecodeToArea(code).let { Box(it.South, it.West, it.North, it.East) }

/** The cells a nudge would step into, drawn faintly so the grid shows where the next door is. */
fun neighbourBoxes(code: String): List<Box> = GPC.Neighbours(code).map(::cellBox)

/**
 * The accuracy below which a fix is called a single cell.
 *
 * The same figure the website uses, so the two never give one fix two verdicts.
 * A cell is 2.56 metres from north to south and at most 3.42 from east to west,
 * so three metres is where a device's own estimate stops fitting inside one.
 */
const val SINGLE_CELL_METRES = 3

/**
 * What a device fix admits about itself.
 *
 * A coarse fix gives an exact code for the wrong doorway, and a reader with
 * nothing else to go on blames the code. So the device's own estimate is shown
 * beside every code it produced, and said plainly when it is wider than a cell.
 */
data class Fix(val metres: Int, val insideOneCell: Boolean)

/** The fix to show for a selection, or null when the point did not come from a device. */
fun fixOf(selection: Selection): Fix? {
    val accuracy = selection.accuracyMetres ?: return null
    if (selection.source != Source.DEVICE) return null
    val within = accuracy.roundToInt()
    return Fix(metres = within, insideOneCell = within <= SINGLE_CELL_METRES)
}
