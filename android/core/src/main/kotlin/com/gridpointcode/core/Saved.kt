package com.gridpointcode.core

/*
 * Saved places: the ones a reader keeps, what they call them, and how to find
 * the door. Kept on the device and nowhere else. There is no account to sync
 * them to, and no export, by the same decision the website made when it took
 * its export down.
 */

/**
 * A place the reader kept.
 *
 * @property code ten characters, as the library writes them
 * @property label what the reader calls it; blank when they did not say
 * @property note directions to the door, which come back with the place
 * @property savedAt when it was saved or last changed, in milliseconds since 1970
 */
data class SavedPlace(
    val code: String,
    val label: String,
    val note: String = "",
    val savedAt: Long,
)

/** The longest name a saved place keeps. A name, not a note: that is what the directions are for. */
const val LABEL_LIMIT = 60

/** A name as it is kept: one line, single spaces, at most [LABEL_LIMIT] characters. */
fun tidyLabel(label: String): String = label.trim().replace(Regex("\\s+"), " ").take(LABEL_LIMIT).trim()

/**
 * The list with a place saved into it, most recent first. One place per code:
 * saving a code that is already saved changes it rather than listing it twice.
 */
fun List<SavedPlace>.saving(place: SavedPlace): List<SavedPlace> =
    listOf(place.copy(label = tidyLabel(place.label), note = tidyNote(place.note))) + filterNot { it.code == place.code }

fun List<SavedPlace>.forgetting(code: String): List<SavedPlace> = filterNot { it.code == code }

/** How the saved places are listed. */
enum class SavedOrder { NEAREST, RECENT }

/**
 * A saved place as the list shows it, with how far it is and which way, when
 * there is somewhere to measure from.
 *
 * @property heading degrees clockwise from north, for an arrow to point along
 */
data class SavedRow(val place: SavedPlace, val metres: Double?, val octant: String?, val heading: Double?)

/**
 * The saved places for the list, measured from [from] when there is a point to
 * measure from. Nearest first when asked for and measurable; otherwise in the
 * order they are kept, most recent first. Places equally far keep that order.
 */
fun savedRows(saved: List<SavedPlace>, from: Point?, order: SavedOrder): List<SavedRow> {
    val rows = saved.map { place ->
        val point = runCatching { selectionOf(place.code, Source.SAVED).point }.getOrNull()
        if (from == null || point == null) {
            SavedRow(place, null, null, null)
        } else {
            SavedRow(place, metresBetween(from, point), bearingBetween(from, point), headingBetween(from, point))
        }
    }
    return if (order == SavedOrder.NEAREST && from != null) rows.sortedBy { it.metres ?: Double.MAX_VALUE } else rows
}

fun List<SavedPlace>.savedAt(code: String): SavedPlace? = firstOrNull { it.code == code }

/**
 * The saved places whose name or directions hold what was typed, for the search
 * field. Folded as a name is, so "cafe" finds "Café Rosa", and matched anywhere
 * in the name, because the word a reader remembers is not always the first.
 */
fun matchSaved(query: String, saved: List<SavedPlace>): List<SavedPlace> {
    val folded = fold(query)
    if (folded.isEmpty()) return emptyList()
    return saved.filter { fold(it.label).contains(folded) || fold(it.note).contains(folded) }
}

/** The saved place Go should take for what was typed: the one whose whole name it is. */
fun namedSaved(query: String, saved: List<SavedPlace>): SavedPlace? {
    val folded = fold(query)
    if (folded.isEmpty()) return null
    return saved.firstOrNull { fold(it.label) == folded }
}
