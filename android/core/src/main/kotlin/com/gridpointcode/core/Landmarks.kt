package com.gridpointcode.core

import ca.pranavpatel.algo.gridpointcode.GPC
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Which landmarks a short form can be given as its reference.
 *
 * A short form is five characters and a place near the point: the listener
 * finds the place, and recovery returns the full code. Section 12.3 bounds when
 * that works, and these rules are the website's own (web/src/lib/reference.ts
 * and landmarks.ts), read from the same archive of landmarks.
 */

/**
 * The recovery bound of section 12.3: within half a level-5 cell in each axis,
 * 0.03598848 degrees of latitude and 0.04798464 of longitude.
 *
 * A box in degrees, and the distinction is not pedantry. The longitude bound is
 * a fixed number of degrees, so the ground it covers shrinks with the cosine of
 * the latitude: a landmark three kilometres due east is well inside the bound
 * at the equator and outside it from about 60 degrees up, which is Oslo,
 * Helsinki and most of Alaska. Anything filtering by a radius in metres is
 * wrong there, and wrong silently: recovery returns another cell's copy of the
 * same five characters, a plausible place eight or ten kilometres away.
 */
object Recovery {
    // Counted in finest rows and columns, as the specification states it: a
    // level-5 cell is 3,125 of them, so half of it is 1,562 whole ones. Exactly
    // half a level-5 cell in degrees is half a finest cell too generous, and
    // that is the half a landmark on the edge of the box would fall into.
    private val finest = GPC.CellDimensions(10)
    private val rows = (GPC.CellDimensions(5).LatitudeSpan / finest.LatitudeSpan).roundToInt() / 2

    val latitude: Double = rows * finest.LatitudeSpan
    val longitude: Double = rows * finest.LongitudeSpan
}

/** What kind of thing a landmark is, in the archive's own order. */
enum class LandmarkKind { STRUCTURE, NATURAL, PLACE }

/** A named place from the archive: named uniquely within its region, so the pair is unambiguous. */
data class Landmark(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val region: String,
    val kind: LandmarkKind,
)

/**
 * A landmark a short form may be given with, as the reader is shown it.
 *
 * @property metres great-circle distance, for the reader and never for the rule
 * @property bearing which way it lies, to the nearest of eight points
 * @property tightness how much of the recovery box it uses: 0 at the point, 1 on the edge
 * @property exact it shares the point's level-5 cell, so a listener who resolves
 *   it by that cell's centre recovers the code whatever anyone's coordinates say
 */
data class Anchor(
    val landmark: Landmark,
    val metres: Double,
    val bearing: String,
    val tightness: Double,
    val exact: Boolean,
)

/**
 * How much of the recovery box a reference uses up. Whichever axis is nearer
 * its limit decides, because either one failing is enough.
 */
fun tightness(point: Point, latitude: Double, longitude: Double): Double = max(
    abs(latitude - point.latitude) / Recovery.latitude,
    abs(wrap(longitude - point.longitude)) / Recovery.longitude,
)

/**
 * The landmarks a short form may be paired with, nearest first.
 *
 * Filtered by the box and sorted by distance, two different measures on
 * purpose: the box is what the specification guarantees, and distance is what a
 * reader understands. Sorting by the box's own fraction would put a landmark
 * further away above a nearer one whenever the axes disagree, which is correct
 * and reads as a mistake.
 */
fun anchorsFor(point: Point, landmarks: Iterable<Landmark>): List<Anchor> {
    val here = runCatching { GPC.Cell(GPC.Encode(point.latitude, point.longitude, false), 5) }
        .getOrNull() ?: return emptyList()
    return landmarks
        .mapNotNull { landmark ->
            val tight = tightness(point, landmark.latitude, landmark.longitude)
            if (tight > 1) return@mapNotNull null
            val theirs = runCatching { GPC.Cell(GPC.Encode(landmark.latitude, landmark.longitude, false), 5) }
                .getOrNull()
            Anchor(
                landmark = landmark,
                metres = metres(point, landmark.latitude, landmark.longitude),
                bearing = bearing(point, landmark.latitude, landmark.longitude),
                tightness = tight,
                exact = theirs == here,
            )
        }
        .sortedBy { it.metres }
}

/**
 * The archive's shards the recovery box reaches into, named by the cell at the
 * archive's level.
 *
 * Taken from the box's four corners rather than its centre, because a box that
 * straddles a boundary is exactly what a centre lookup misses, and missing a
 * shard raises nothing: it just quietly shortens the list.
 */
fun shardsFor(point: Point, level: Int): List<String> {
    val shards = linkedSetOf<String>()
    for (dy in intArrayOf(-1, 1)) {
        for (dx in intArrayOf(-1, 1)) {
            val latitude = (point.latitude + dy * Recovery.latitude).coerceIn(-90.0, 90.0)
            val longitude = wrap(point.longitude + dx * Recovery.longitude)
            // A corner past the pole or in the reserved range has no shard. The
            // others still do, and the box is clipped to what the world holds.
            runCatching { GPC.Cell(GPC.Encode(latitude, longitude, false), level) }
                .onSuccess { shards += it }
        }
    }
    return shards.toList()
}

/**
 * The line a reader shares, as section 12.1 writes it: the short form with its
 * leading dash and no hash, then the place. Code first, because a dash at the
 * end reads as a minus sign; "near", because the point can be kilometres off;
 * the region spelled out, because it is what tells two places of one name apart.
 */
fun anchored(code: String, landmark: Landmark): String {
    val place = if (landmark.region.isEmpty()) landmark.name else "${landmark.name}, ${landmark.region}"
    return "-${GPC.Shorten(code)} near $place"
}

/** A longitude difference carried into the range holding the short way round. */
private fun wrap(degrees: Double): Double = ((degrees + 540) % 360) - 180

private const val EARTH_METRES = 6371008.8

private fun radians(degrees: Double) = degrees * PI / 180

private fun metres(point: Point, latitude: Double, longitude: Double): Double {
    val dLat = radians(latitude - point.latitude)
    val dLng = radians(wrap(longitude - point.longitude))
    val a = sin(dLat / 2).let { it * it } +
        cos(radians(point.latitude)) * cos(radians(latitude)) * sin(dLng / 2).let { it * it }
    return 2 * EARTH_METRES * asin(min(1.0, sqrt(a)))
}

/**
 * Not the nudge pad's [Compass]. The two hold the same eight names and mean
 * different things: that one is the order the library returns neighbouring
 * cells in, this one is a direction on the ground.
 */
private val OCTANTS = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

private fun bearing(point: Point, latitude: Double, longitude: Double): String {
    val dLng = radians(wrap(longitude - point.longitude))
    val y = sin(dLng) * cos(radians(latitude))
    val x = cos(radians(point.latitude)) * sin(radians(latitude)) -
        sin(radians(point.latitude)) * cos(radians(latitude)) * cos(dLng)
    val degrees = atan2(y, x) * 180 / PI
    return OCTANTS[(((degrees + 360) % 360) / 45).roundToInt() % 8]
}

/** How many shards the kept area reaches out from the place's own, every way. */
private const val AREA_REACH = 2

/**
 * The shards of the area kept around a point: its own shard and two more every
 * way, a block of twenty-five, 200 km north to south.
 *
 * Centred on the place rather than a cell of the grid. A cell one level up is
 * the same size, and is what the website keeps, but it is fixed to the grid:
 * downtown Toronto sits in the corner of its cell, which reaches 190 km west and
 * stops 15 km east. Large enough to be worth keeping before a journey, small
 * enough to be a few hundred kilobytes rather than the eighty-odd megabytes the
 * whole archive weighs.
 *
 * Found by stepping whole shards from the point, which lands in each neighbour
 * at the point's own place within it. Fewer than twenty-five near a pole, where
 * the rows run out.
 */
fun areaAround(point: Point, level: Int): List<String> {
    val cell = GPC.CellDimensions(level)
    val shards = linkedSetOf<String>()
    for (row in -AREA_REACH..AREA_REACH) {
        for (column in -AREA_REACH..AREA_REACH) {
            val latitude = point.latitude + row * cell.LatitudeSpan
            if (latitude < -90 || latitude > 90) continue
            val longitude = wrap(point.longitude + column * cell.LongitudeSpan)
            runCatching { GPC.Cell(GPC.Encode(latitude, longitude, false), level) }
                .onSuccess { shards += it }
        }
    }
    return shards.toList()
}

/** The kept area's size on the ground at a latitude, in metres: north to south, then east to west. */
fun areaMetres(latitude: Double, level: Int): Pair<Double, Double> {
    val cell = GPC.CellDimensions(level)
    val across = 2 * AREA_REACH + 1
    return across * cell.NorthSouth to across * cell.EastWest * cos(radians(latitude))
}
