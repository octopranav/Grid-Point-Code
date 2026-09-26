package com.gridpointcode.core

/**
 * A round of saved places, in the order to walk them.
 *
 * Worked out on the device, from straight lines: no server, no map matching,
 * no account. Not by sorting codes: codes sort by cell, and neighbouring cells
 * can sit either side of a boundary that the order jumps across, so a round
 * in code order came out half as long again as this one, over two hundred
 * sets of twenty stops scattered across a city, with single legs longer than
 * the city was wide. This takes the nearest stop next, then uncrosses the
 * legs until no swap of two of them makes the round shorter.
 *
 * @property stops the places, in order, each with its leg from the one before
 * @property metres the whole round, in straight lines
 */
data class Round(val stops: List<Stop>, val metres: Double)

/**
 * One stop of a round.
 *
 * @property leg the straight line from the stop before, or from the start
 */
data class Stop(val place: SavedPlace, val point: Point, val leg: Double)

/**
 * [places] in the order to walk them from [start], where the reader is, or
 * from whichever stop gives the shortest of the rounds tried when there is no
 * start. The round is short, not proven the shortest there is. The end
 * is wherever the round finishes: nobody has to walk back. A place whose code
 * does not read is left out.
 */
fun roundOf(places: List<SavedPlace>, start: Point?): Round {
    val stops = places.mapNotNull { place -> runCatching { place to selectionOf(place.code, Source.SAVED).point }.getOrNull() }
    if (stops.isEmpty()) return Round(emptyList(), 0.0)
    val points = stops.map { it.second }
    val order = if (start != null) {
        uncrossed(listOf(start) + points, fixedStart = true).drop(1).map { it - 1 }
    } else {
        // Every stop tried as the first, the shortest kept: an open round has
        // no given start, and the nearest-next order depends on where it begins.
        points.indices
            .map { first -> uncrossed(points, fixedStart = false, first = first) }
            .minBy { length(it.map(points::get)) }
    }
    var previous = start
    val ordered = order.map { index ->
        val (place, point) = stops[index]
        Stop(place, point, previous?.let { metresBetween(it, point) } ?: 0.0).also { previous = point }
    }
    return Round(ordered, ordered.sumOf { it.leg })
}

/**
 * The indices of [points] in a short open order: nearest next from [first],
 * then any two legs swapped while that shortens the whole. With [fixedStart]
 * the first point stays first, as the reader's own position must.
 */
private fun uncrossed(points: List<Point>, fixedStart: Boolean, first: Int = 0): List<Int> {
    val order = mutableListOf(first)
    val left = points.indices.filter { it != first }.toMutableList()
    while (left.isNotEmpty()) {
        val here = points[order.last()]
        // The first of equally near stops, so the same stops give the same round.
        val next = left.minBy { metresBetween(here, points[it]) }
        left.remove(next)
        order += next
    }
    fun at(i: Int) = points[order[i]]
    fun gap(a: Int, b: Int) = metresBetween(at(a), at(b))
    var improved = true
    var passes = 0
    while (improved && passes < MAX_PASSES) {
        improved = false
        passes += 1
        for (i in (if (fixedStart) 1 else 0) until order.size - 1) {
            for (j in i + 1 until order.size) {
                // Reversing i..j replaces the legs into i and out of j. An open
                // round has no leg before its first stop or after its last.
                val before = (if (i > 0) gap(i - 1, i) else 0.0) + (if (j < order.size - 1) gap(j, j + 1) else 0.0)
                val after = (if (i > 0) gap(i - 1, j) else 0.0) + (if (j < order.size - 1) gap(i, j + 1) else 0.0)
                if (after < before - SHORTER_BY) {
                    order.subList(i, j + 1).reverse()
                    improved = true
                }
            }
        }
    }
    return order
}

private fun length(points: List<Point>): Double = points.zipWithNext { a, b -> metresBetween(a, b) }.sum()

/** Enough for any round a person walks; each pass only ever shortens it. */
private const val MAX_PASSES = 100

/** A centimetre: shorter than that is rounding, not a better round. */
private const val SHORTER_BY = 0.01
