package com.gridpointcode.core

import ca.pranavpatel.algo.gridpointcode.GPC

/**
 * Where a point came from.
 *
 * It is kept because the screen owes the reader a different sentence for each:
 * a device fix has an accuracy worth showing, a typed code has none to show, and
 * a nudge is a deliberate step rather than a measurement.
 */
enum class Source {
    DEVICE,
    MAP,
    CODE,
    LINK,
    SEARCH,
    NUDGE,
    SAMPLE,
}

/** A point on the earth, in degrees. */
data class Point(val latitude: Double, val longitude: Double)

/**
 * The one thing the interface reads.
 *
 * Everything on a screen is derived from a selection: the mark, the written
 * forms, the nudge pad, the link, the spoken line. Nothing writes a surface
 * directly, so no surface can go on describing a place that has been left.
 *
 * @property code ten characters, unformatted, as the library returns them
 * @property accuracyMetres the radius a device fix claims, when there was one
 */
data class Selection(
    val point: Point,
    val code: String,
    val source: Source,
    val accuracyMetres: Double? = null,
)

/** The selection for a point, with its code computed once. */
fun selectionAt(
    point: Point,
    source: Source,
    accuracyMetres: Double? = null,
): Selection = Selection(
    point = point,
    code = GPC.Encode(point.latitude, point.longitude, false),
    source = source,
    accuracyMetres = accuracyMetres,
)

/** The selection for a code, placed at the centre of the cell it names. */
fun selectionOf(code: String, source: Source): Selection {
    val clean = GPC.Normalise(code)[0]
    val centre = GPC.Decode(clean)
    return Selection(
        point = Point(centre.Latitude, centre.Longitude),
        code = clean,
        source = source,
    )
}
