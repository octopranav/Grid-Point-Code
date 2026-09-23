package com.gridpointcode.core

import ca.pranavpatel.algo.gridpointcode.GPC
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** The website, which also serves the name index the app searches. */
const val SITE = "https://gridpointcode.com"

/** Where a link points. The app and the site hand out the same address. */
const val PLACE_ADDRESS = "$SITE/play"

/** The query parameter holding the code. One letter, because it is typed by hand. */
const val CODE_PARAM = "c"

/** The query parameter holding the directions, when there are any. */
const val NOTE_PARAM = "n"

/**
 * The longest directions a link will carry.
 *
 * A sentence, not a paragraph. A square code grows with every character, and one
 * too dense to read off a doorway defeats the reason it is there.
 */
const val NOTE_LIMIT = 80

/**
 * The query naming a code, and the directions if there are any.
 *
 * The code goes in unformatted: a hash starts a fragment in a URL and would take
 * the code with it. The ten characters need no escaping, and a reader who pastes
 * the printed form back gets the same place anyway.
 */
fun linkTo(code: String, note: String = ""): String {
    val query = StringBuilder("?").append(CODE_PARAM).append('=').append(GPC.Normalise(code)[0])
    val said = tidyNote(note)
    if (said.isNotEmpty()) {
        query.append('&').append(NOTE_PARAM).append('=').append(encode(said))
    }
    return query.toString()
}

/** The whole address, for sharing or for a square code. */
fun addressOf(code: String, note: String = "", base: String = PLACE_ADDRESS): String =
    base + linkTo(code, note)

/** The code an address names, exactly as it was written there, or null. */
fun codeIn(address: String): String? = parameterIn(address, CODE_PARAM)?.takeIf { it.isNotBlank() }

/** The directions an address carries, tidied, or an empty string. */
fun noteIn(address: String): String = tidyNote(parameterIn(address, NOTE_PARAM) ?: "")

/** Directions as a link carries them: one line, trimmed, and not too long. */
fun tidyNote(note: String): String =
    note.replace(WHITESPACE, " ").trim().take(NOTE_LIMIT)

private val WHITESPACE = Regex("\\s+")

private fun parameterIn(address: String, name: String): String? {
    val query = address.substringAfter('?', "").substringBefore('#')
    if (query.isEmpty()) return null
    for (pair in query.split('&')) {
        val key = pair.substringBefore('=')
        if (key == name) return decode(pair.substringAfter('=', ""))
    }
    return null
}

private fun encode(text: String): String =
    URLEncoder.encode(text, StandardCharsets.UTF_8.name())

private fun decode(text: String): String =
    runCatching { URLDecoder.decode(text, StandardCharsets.UTF_8.name()) }.getOrDefault(text)
