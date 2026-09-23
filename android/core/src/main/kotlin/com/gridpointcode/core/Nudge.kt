package com.gridpointcode.core

import ca.pranavpatel.algo.gridpointcode.GPC

/** The eight directions a nudge can take, clockwise from north. */
enum class Compass { N, NE, E, SE, S, SW, W, NW }

/**
 * The eight cells around this one, keyed by the direction they lie in.
 *
 * The library returns them clockwise from north. That order is checked by the
 * tests, which decode every neighbour and compare it with the centre, rather
 * than trusted from a comment.
 *
 * At the poles the grid does not wrap and fewer than eight come back. No
 * direction can then be named honestly, so nothing is returned and the pad is
 * not offered.
 */
fun around(code: String): Map<Compass, String> {
    val cells = GPC.Neighbours(code)
    if (cells.size != Compass.entries.size) return emptyMap()
    return Compass.entries.zip(cells).toMap()
}

/** Where each direction sits on a three by three pad, read left to right. Null is the centre. */
val PAD: List<Compass?> = listOf(
    Compass.NW, Compass.N, Compass.NE,
    Compass.W, null, Compass.E,
    Compass.SW, Compass.S, Compass.SE,
)
