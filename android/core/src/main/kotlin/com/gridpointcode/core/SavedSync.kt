package com.gridpointcode.core

/*
 * Saved places as they travel between the phone and the watch.
 *
 * The phone and the watch are two apps, and the Wear Data Layer carries bytes
 * between them. This is those bytes: one header line, then one place to a line,
 * its fields apart by tabs. Written here, in plain Kotlin, so both apps read and
 * write the same thing and the tests can hold them to it without a device.
 *
 * Names and directions are tidied to one line before they are kept, but the
 * encoding does not lean on that: a backslash, a tab or a line break inside a
 * field is escaped, so whatever is kept arrives exactly.
 */

/** Where the phone keeps the list in the Data Layer, for the watch to read. */
const val SAVED_PATH = "/saved"

/** Where the watch leaves a place it saved, one item a place, for the phone to take in. */
const val FROM_WATCH_PATH = "/from-watch"

/**
 * What the watch app says it is to the Data Layer, so the phone sends the list
 * only to a watch that has the app. The watch declares it in its resources, in
 * wear.xml, which must name the same.
 */
const val WATCH_CAPABILITY = "gpc_watch"

/** The first line of every list, naming the encoding and its version. */
internal const val SAVED_HEADER = "gpc-saved 1"

/** The list, written for the other device. */
fun encodeSaved(places: List<SavedPlace>): String = buildString {
    append(SAVED_HEADER).append('\n')
    places.forEach { place ->
        append(listOf(place.code, place.savedAt.toString(), place.label, place.note).joinToString("\t") { escape(it) })
        append('\n')
    }
}

/**
 * The list the other device wrote. A line that is not a place, or names no
 * place the library can read, is left out rather than failing the rest; text
 * with no header, or another version's, is no list at all.
 */
fun decodeSaved(text: String): List<SavedPlace> {
    val lines = text.split('\n')
    if (lines.firstOrNull() != SAVED_HEADER) return emptyList()
    return lines.drop(1).filter { it.isNotEmpty() }.mapNotNull { line ->
        val fields = split(line)
        if (fields.size != 4) return@mapNotNull null
        val code = fields[0]
        val savedAt = fields[1].toLongOrNull() ?: return@mapNotNull null
        runCatching { selectionOf(code, Source.SAVED) }.getOrNull() ?: return@mapNotNull null
        SavedPlace(code = code, label = fields[2], note = fields[3], savedAt = savedAt)
    }
}

/**
 * The other device's whole list, or null when [text] is not one to take: no
 * header, another version's, or lines of which none read. That last is damage,
 * not a list emptied on purpose, and must not empty the list it replaces.
 */
fun decodeSavedList(text: String): List<SavedPlace>? {
    val lines = text.split('\n')
    if (lines.firstOrNull() != SAVED_HEADER) return null
    val places = decodeSaved(text)
    return if (places.isEmpty() && lines.drop(1).any { it.isNotEmpty() }) null else places
}

/**
 * The phone's list with places the watch saved merged in: the later saving of
 * a code wins, and the list stays most recent first.
 */
fun List<SavedPlace>.merging(arrived: List<SavedPlace>): List<SavedPlace> =
    arrived.fold(this) { list, place ->
        val kept = list.savedAt(place.code)
        if (kept != null && kept.savedAt >= place.savedAt) list else list.saving(place)
    }.sortedByDescending { it.savedAt }

private fun escape(field: String): String =
    field.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r")

/** A line's fields, split at tabs that are not escaped, each unescaped. */
private fun split(line: String): List<String> {
    val fields = mutableListOf<String>()
    val field = StringBuilder()
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            c == '\\' && i + 1 < line.length -> {
                field.append(
                    when (line[i + 1]) {
                        't' -> '\t'
                        'n' -> '\n'
                        'r' -> '\r'
                        else -> line[i + 1]
                    },
                )
                i += 2
                continue
            }
            c == '\t' -> {
                fields += field.toString()
                field.clear()
            }
            else -> field.append(c)
        }
        i += 1
    }
    fields += field.toString()
    return fields
}
