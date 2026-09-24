package com.gridpointcode.core

import ca.pranavpatel.algo.gridpointcode.GPC

/*
 * Areas. A code names a doorway, and its first few characters name the cell
 * that holds it (section 18.1): five a district eight kilometres across, seven
 * a few streets, eight a block. A market, a delivery zone or a flooded district
 * is written in the same alphabet as a door, and every code inside it begins
 * with it. A cell is written with no hash and no hyphen, so it is never taken
 * for a code: ten characters is a code, and anything shorter is a region.
 */

/** An area: the cell the first [level] characters of a code name. */
data class AreaCell(val cell: String, val box: Box) {
    val level: Int get() = cell.length
    val centre: Point get() = Point((box.south + box.north) / 2, (box.west + box.east) / 2)
}

/** The areas worth offering around a place, largest first: a region down to a building. */
val AREA_LEVELS: IntProgression = 3..9

/**
 * The area a cell names, or null when the text is not one: one to nine symbols,
 * normalised as a code is, so a confusable letter reads as the symbol it stands
 * for, and never a cell in the reserved range.
 *
 * The edges come from the grid rows and columns of any code inside the cell,
 * the way section 18.3 counts them, so the north edge of one cell is exactly
 * the south edge of the next and no seam opens between them.
 */
fun areaOf(text: String): AreaCell? {
    val given = text.trim()
    if (given.isEmpty() || given.startsWith('#') || given.contains('-')) return null
    val cleaned = runCatching { GPC.Normalise(given)[0] }.getOrNull() ?: return null
    if (cleaned.length !in 1..9) return null
    return runCatching {
        val inside = cleaned.padEnd(10, '0')
        check(GPC.Cell(inside, cleaned.length) == cleaned)
        val grid = GPC.DecodeToGrid(inside)
        var span = 1L
        repeat(10 - cleaned.length) { span *= 5 }
        val row = grid[0] / span * span
        val column = grid[1] / span * span
        val unit = GPC.CellDimensions(10)
        AreaCell(
            cell = cleaned,
            box = Box(
                south = row * unit.LatitudeSpan - 90,
                west = column * unit.LongitudeSpan - 180,
                north = (row + span) * unit.LatitudeSpan - 90,
                east = (column + span) * unit.LongitudeSpan - 180,
            ),
        )
    }.getOrNull()
}

/** The area of a level that holds a code: its first [level] characters. */
fun areaHolding(code: String, level: Int): AreaCell? =
    runCatching { GPC.Cell(code, level) }.getOrNull()?.let(::areaOf)

/**
 * Whether typed text should be read as an area without being asked: symbols of
 * both kinds, as a cell nearly always has. A run of letters alone is a word as
 * often as not, "CNN" or "Mt", and a run of digits alone is a postcode or a
 * house number; either is offered as an area beside its other reading instead.
 */
internal fun looksLikeArea(text: String): Boolean {
    val symbols = text.filter { !it.isWhitespace() }
    return symbols.any { it.isLetter() } && symbols.any { it.isDigit() }
}
