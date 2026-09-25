package com.gridpointcode.core

import ca.pranavpatel.algo.gridpointcode.GPC

/*
 * A code that may have been heard wrong. Section 15 of the specification: a
 * wrong character in positions four to six lands a code tens of kilometres
 * away, near enough to look like an answer, and the format cannot tell. What it
 * can do is locate the slip: of the codes one slip away, a character changed or
 * two swapped, the ones that land near where the reader is are nearly always
 * the code that was meant (section 15.3, whose ranking the library implements).
 *
 * So nothing here says a code is wrong; the specification forbids claiming to
 * detect typos. It says a code lands far from the reader, and offers the codes
 * one slip away that land near them, for the reader to choose or not.
 */

/** How a suggestion differs from what was typed. */
sealed interface Slip {
    /** One character changed: [position] counts from 1, [was] is what was typed there. */
    data class Replaced(val position: Int, val was: Char) : Slip

    /** Two neighbouring characters swapped, at [position] and the one after it. */
    data class Swapped(val position: Int) : Slip
}

/** A code one slip away from what was typed, where it lands, and how it differs. */
data class Suggestion(val code: String, val metres: Double, val bearing: String, val slip: Slip)

/**
 * A typed code that lands far from the reader, with the codes one slip away
 * that land near them.
 *
 * @property typed the code as typed, ten characters
 * @property metres from the reference to where the typed code lands
 * @property bearing the direction from the reference, as eight points of the compass
 * @property fromDevice the reference is the device's fix, not the place the reader had
 * @property checked how many codes one slip away were looked at: 249, fewer when
 *   neighbouring characters repeat, since swapping them changes nothing
 */
data class Doubt(
    val typed: String,
    val metres: Double,
    val bearing: String,
    val fromDevice: Boolean,
    val checked: Int,
    val suggestions: List<Suggestion>,
)

/**
 * The level whose 3 by 3 window of cells counts as near: section 15.3's
 * default, which suits a device fix or a place the reader had, and returns one
 * candidate in the median case.
 */
const val CORRECTION_LEVEL = 6

/** No more than this many are offered; past the first few, a list is a guess. */
private const val OFFERED = 3

/**
 * The doubt about a typed code, or null when there is none: when the code
 * already lands within the window around the reference, or when nothing one
 * slip away lands there either.
 */
fun doubtAbout(code: String, reference: Point, fromDevice: Boolean): Doubt? {
    val typed = runCatching { GPC.Normalise(code)[0] }.getOrNull()?.takeIf { it.length == 10 } ?: return null
    val landed = runCatching { selectionOf(typed, Source.CODE).point }.getOrNull() ?: return null
    val near = GPC.Cell(GPC.Encode(reference.latitude, reference.longitude, false), CORRECTION_LEVEL)
    val cell = GPC.Cell(typed, CORRECTION_LEVEL)
    if (cell == near || cell in GPC.Neighbours(near)) return null

    val suggestions = GPC.SuggestCorrections(typed, reference.latitude, reference.longitude, CORRECTION_LEVEL, false)
        .take(OFFERED)
        .mapNotNull { other ->
            val point = runCatching { selectionOf(other, Source.CODE).point }.getOrNull() ?: return@mapNotNull null
            val slip = slipBetween(typed, other) ?: return@mapNotNull null
            Suggestion(other, metresBetween(reference, point), bearingBetween(reference, point), slip)
        }
    if (suggestions.isEmpty()) return null
    return Doubt(
        typed = typed,
        metres = metresBetween(reference, landed),
        bearing = bearingBetween(reference, landed),
        fromDevice = fromDevice,
        checked = candidatesFor(typed),
        suggestions = suggestions,
    )
}

/**
 * How many codes are one slip away, as section 15.3 counts them: 240 changed
 * characters, and a swap for each pair of neighbours that differ.
 */
internal fun candidatesFor(typed: String): Int = 240 + (0 until typed.length - 1).count { typed[it] != typed[it + 1] }

/** How [other] differs from [typed]: one character changed, or two neighbours swapped. */
internal fun slipBetween(typed: String, other: String): Slip? {
    val different = typed.indices.filter { typed[it] != other[it] }
    return when {
        different.size == 1 -> Slip.Replaced(different[0] + 1, typed[different[0]])
        different.size == 2 && different[1] == different[0] + 1 &&
            typed[different[0]] == other[different[1]] && typed[different[1]] == other[different[0]] ->
            Slip.Swapped(different[0] + 1)
        else -> null
    }
}
