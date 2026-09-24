package com.gridpointcode.map

/**
 * The map's credit line, as HTML with its links.
 *
 * Every basemap here is drawn from OpenStreetMap data, whose licence asks for
 * credit in a corner of the map with a way to its copyright page, and the tile
 * provider asks for its own name and the schema's beside it. The words come
 * from the map's sources, each of which declares its own, so a different tile
 * host brings its own credit with it; the sheet and the search bar cover the
 * map library's own control, so the app draws the line itself where it can be
 * seen. Shown in full, as the website shows it, rather than folded behind a
 * button: one line costs little, and folding it would need a reason.
 *
 * Until a source has said, which is the moment between the style arriving and
 * its tiles' description, the provider's own line stands in, word for word.
 */
internal fun creditOf(declared: List<String?>): String =
    declared
        .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
        .distinct()
        .joinToString(" ")
        .ifEmpty { PROVIDER_CREDIT }

/** The tile provider's line, as its own quick start gives it and its tiles declare it. */
internal const val PROVIDER_CREDIT =
    "<a href=\"https://openfreemap.org\">OpenFreeMap</a> " +
        "<a href=\"https://www.openmaptiles.org/\">&copy; OpenMapTiles</a> " +
        "Data from <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a>"
