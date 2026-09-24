package com.gridpointcode.core

import ca.pranavpatel.algo.gridpointcode.GPC

/**
 * A code as it should be said out loud, which is always the check form.
 *
 * The alphabet was chosen so that a code cannot spell a word and cannot be
 * misread on paper. It was never chosen to be said aloud, and cannot be now:
 * spoken in English, C, D, G, P, T and the digit three all rhyme. A listener who
 * hears D where T was said writes down a code that parses, validates, and points
 * somewhere else entirely.
 *
 * Callout words are what avoid that, and the check character is what catches it
 * when they fail. So the check character is added here rather than expected from
 * the caller: a line dictated without one is the single case the specification
 * names as a mistake, and asking every screen to remember that is how it gets
 * forgotten. Adding it is harmless to a code that already carries one.
 *
 * The words are a parameter because the code is the same string in every
 * language and only the words around it change. That is the one place where
 * this format is easier to hand to a stranger than an address made of words.
 */
fun aloud(code: String, spelling: Spelling = INTERNATIONAL): String {
    val parts = GPC.Normalise(GPC.WithCheck(code))
    val payload = parts[0]
    val check = parts[1]
        ?: error("the check form has no check character")

    val head = payload.take(5).map { say(it, spelling) }
    val tail = payload.drop(5).map { say(it, spelling) }
    return head.joinToString(", ") + "; " +
        tail.joinToString(", ") + "; " + spelling.check + " " + say(check[0], spelling)
}

/**
 * An area spoken aloud: its symbols, one word each. No check word, because the
 * check character belongs to a whole code and a cell has none.
 */
fun aloudArea(cell: String, spelling: Spelling = INTERNATIONAL): String =
    cell.map { say(it, spelling) }.joinToString(", ")

/**
 * Coordinates as written, `43.650006, -79.380004`, said in the listener's words:
 * the whole degrees as a number, then every decimal digit on its own.
 *
 * Read from the written text rather than from the numbers, so what is heard is
 * what is on the screen, sign and all. The digits are said one by one because a
 * speech engine for a language that writes a decimal comma may take the full
 * stop in "43.650006" for a thousands separator.
 */
fun aloudCoordinates(written: String, spelling: Spelling = INTERNATIONAL): String =
    written.split(',').joinToString(", ") { part ->
        val value = part.trim()
        val sign = if (value.startsWith('-')) spelling.minus + " " else ""
        val (whole, fraction) = value.removePrefix("-").split('.').let { it[0] to it.getOrElse(1) { "" } }
        val digits = fraction.map { spelling.digits[Character.digit(it, 10)] }
        sign + whole + if (digits.isEmpty()) "" else " " + spelling.point + " " + digits.joinToString(" ")
    }

private fun say(symbol: Char, spelling: Spelling): String =
    spelling.letters[symbol] ?: spelling.digits[Character.digit(symbol, 10)]
