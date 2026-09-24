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
fun aloud(
    code: String,
    callouts: Map<Char, String> = RADIO_ALPHABET,
    checkWord: String = "check",
): String {
    val parts = GPC.Normalise(GPC.WithCheck(code))
    val payload = parts[0]
    val check = parts[1]
        ?: error("the check form has no check character")

    val head = payload.take(5).map { say(it, callouts) }
    val tail = payload.drop(5).map { say(it, callouts) }
    return head.joinToString(", ") + "; " +
        tail.joinToString(", ") + "; " + checkWord + " " + say(check[0], callouts)
}

/**
 * An area spoken aloud: its symbols, one word each. No check word, because the
 * check character belongs to a whole code and a cell has none.
 */
fun aloudArea(cell: String, callouts: Map<Char, String> = RADIO_ALPHABET): String =
    cell.map { say(it, callouts) }.joinToString(", ")

private fun say(symbol: Char, callouts: Map<Char, String>): String =
    callouts[symbol] ?: NUMBERS[Character.digit(symbol, 10)]

/**
 * The international radiotelephony words, which the specification prints as a
 * reference rather than a rule. An application serving one region should hand in
 * the words its own listeners use.
 */
val RADIO_ALPHABET: Map<Char, String> = mapOf(
    'C' to "Charlie",
    'D' to "Delta",
    'F' to "Foxtrot",
    'G' to "Golf",
    'H' to "Hotel",
    'J' to "Juliett",
    'K' to "Kilo",
    'L' to "Lima",
    'M' to "Mike",
    'N' to "November",
    'P' to "Papa",
    'R' to "Romeo",
    'T' to "Tango",
    'W' to "Whiskey",
    'X' to "X-ray",
)

/** Digits are said as the number, because no callout word begins with a seven. */
private val NUMBERS = listOf(
    "zero", "one", "two", "three", "four",
    "five", "six", "seven", "eight", "nine",
)
