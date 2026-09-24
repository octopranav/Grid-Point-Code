package com.gridpointcode.core

import java.util.Locale

/*
 * The words a code is spelled in, one set per listener's language.
 *
 * The code is the same ten characters in every language; only the words that
 * carry them change. Appendix D.2 of the specification gives the rule, that a
 * callout for a symbol is any word beginning with it, and leaves the words to
 * the application. So each set below is a table its own speakers already know,
 * written out for the fifteen letters of the alphabet, with the digits said as
 * numbers, as the appendix has it.
 *
 * Every word begins with its letter, because the listener keeps the first
 * character and discards the rest. Where a table says a letter's name instead
 * of a word, as the Italian one does for the letters Italian borrows, the
 * table's own alternative word is used: the name of K in Italian begins with C.
 */

/**
 * The words of one language, and the few around them when a line is read out.
 *
 * @property tag the language, as a BCP 47 tag, which is also the voice asked for
 * @property name the language's name in itself, as a list of languages shows it
 * @property letters a word for each letter of the alphabet
 * @property digits the numbers nought to nine, as they are said
 * @property check what names the check character when it is read out
 * @property point what is said for the decimal point in a coordinate
 * @property minus what is said for a minus sign
 * @property emergency the emergency card's line, the coordinates then the code
 */
data class Spelling(
    val tag: String,
    val name: String,
    val letters: Map<Char, String>,
    val digits: List<String>,
    val check: String,
    val point: String,
    val minus: String,
    val emergency: String,
) {
    val locale: Locale get() = Locale.forLanguageTag(tag)

    /** The first few words, which tell a listener's table apart at a glance. */
    val sample: String get() = "CDF".map { letters.getValue(it) }.joinToString(", ")
}

/**
 * The international radiotelephony words, which the specification prints as a
 * reference, and the set for anybody whose language has none here.
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

val INTERNATIONAL = Spelling(
    tag = "en",
    name = "English",
    letters = RADIO_ALPHABET,
    digits = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"),
    check = "check",
    point = "point",
    minus = "minus",
    emergency = "Latitude and longitude: %1\$s. Grid Point Code: %2\$s.",
)

/** DIN 5009, as revised in 2022: towns, in place of the names used before. Two is "zwo", so it cannot be heard as three. */
private val GERMAN = Spelling(
    tag = "de",
    name = "Deutsch",
    letters = words(
        "Chemnitz", "Düsseldorf", "Frankfurt", "Goslar", "Hamburg", "Jena", "Köln", "Leipzig",
        "München", "Nürnberg", "Potsdam", "Rostock", "Tübingen", "Wuppertal", "Xanten",
    ),
    digits = listOf("null", "eins", "zwo", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun"),
    check = "Prüfzeichen",
    point = "Komma",
    minus = "minus",
    emergency = "Breitengrad und Längengrad: %1\$s. Grid Point Code: %2\$s.",
)

/**
 * No national standard exists. These follow the table the telephone service in
 * Spain used, with Granada for G: in Spanish the G of Gerona is said as a J.
 */
private val SPANISH = Spelling(
    tag = "es",
    name = "Español",
    letters = words(
        "Carmen", "Dolores", "Francia", "Granada", "Historia", "José", "Kilo", "Lorenzo",
        "Madrid", "Navarra", "París", "Ramón", "Toledo", "Washington", "Xilófono",
    ),
    digits = listOf("cero", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve"),
    check = "control",
    point = "coma",
    minus = "menos",
    emergency = "Latitud y longitud: %1\$s. Grid Point Code: %2\$s.",
)

/** The French table of first names. */
private val FRENCH = Spelling(
    tag = "fr",
    name = "Français",
    letters = words(
        "Célestin", "Désiré", "François", "Gaston", "Henri", "Joseph", "Kléber", "Louis",
        "Marcel", "Nicolas", "Pierre", "Raoul", "Thérèse", "William", "Xavier",
    ),
    digits = listOf("zéro", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf"),
    check = "contrôle",
    point = "virgule",
    minus = "moins",
    emergency = "Latitude et longitude : %1\$s. Grid Point Code : %2\$s.",
)

/** The Italian table of towns, with its alternative words for the letters Italian borrows. */
private val ITALIAN = Spelling(
    tag = "it",
    name = "Italiano",
    letters = words(
        "Como", "Domodossola", "Firenze", "Genova", "Hotel", "Jolly", "Kursaal", "Livorno",
        "Milano", "Napoli", "Palermo", "Roma", "Torino", "Washington", "Xilofono",
    ),
    digits = listOf("zero", "uno", "due", "tre", "quattro", "cinque", "sei", "sette", "otto", "nove"),
    check = "controllo",
    point = "virgola",
    minus = "meno",
    emergency = "Latitudine e longitudine: %1\$s. Grid Point Code: %2\$s.",
)

/** The official table of the Netherlands. One is written "één", so it is said as the number and not the article. */
private val DUTCH = Spelling(
    tag = "nl",
    name = "Nederlands",
    letters = words(
        "Cornelis", "Dirk", "Ferdinand", "Gerard", "Hendrik", "Jan", "Karel", "Lodewijk",
        "Maria", "Nico", "Pieter", "Rudolf", "Tinus", "Willem", "Xantippe",
    ),
    digits = listOf("nul", "één", "twee", "drie", "vier", "vijf", "zes", "zeven", "acht", "negen"),
    check = "controle",
    point = "komma",
    minus = "min",
    emergency = "Breedtegraad en lengtegraad: %1\$s. Grid Point Code: %2\$s.",
)

/** The Swedish civil table of first names. */
private val SWEDISH = Spelling(
    tag = "sv",
    name = "Svenska",
    letters = words(
        "Cesar", "David", "Filip", "Gustav", "Helge", "Johan", "Kalle", "Ludvig",
        "Martin", "Niklas", "Petter", "Rudolf", "Tore", "Wilhelm", "Xerxes",
    ),
    digits = listOf("noll", "ett", "två", "tre", "fyra", "fem", "sex", "sju", "åtta", "nio"),
    check = "kontroll",
    point = "komma",
    minus = "minus",
    emergency = "Latitud och longitud: %1\$s. Grid Point Code: %2\$s.",
)

/** Every listener's language offered, in the order a list of languages is read: by its own name. */
val SPELLINGS: List<Spelling> = listOf(GERMAN, INTERNATIONAL, SPANISH, FRENCH, ITALIAN, DUTCH, SWEDISH)

/** The set for a language, or the international one when there is none for it. */
fun spellingFor(locale: Locale): Spelling =
    SPELLINGS.firstOrNull { it.locale.language == locale.language } ?: INTERNATIONAL

/** The set for a stored tag, or null when the tag names none of them. */
fun spellingTagged(tag: String): Spelling? = SPELLINGS.firstOrNull { it.tag == tag }

/** The fifteen letters of the alphabet, in its order, each paired with its word. */
private fun words(vararg said: String): Map<Char, String> = "CDFGHJKLMNPRTWX".toList().zip(said).toMap()
