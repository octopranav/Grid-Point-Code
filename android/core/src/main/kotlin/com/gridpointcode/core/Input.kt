package com.gridpointcode.core

import ca.pranavpatel.algo.gridpointcode.CodeClass
import ca.pranavpatel.algo.gridpointcode.GPC
import ca.pranavpatel.algo.gridpointcode.GPCException

/**
 * What a piece of pasted or typed text turned out to be.
 *
 * One field takes everything, so one reader decides what was meant. The verdict
 * on a code is always the library's: this file only works out which question to
 * ask it.
 */
sealed interface Reading {
    /** A full code, with the directions a link carried alongside it, if any. */
    data class Code(val code: String, val note: String = "") : Reading

    /** A short form, which names nowhere until it is given a reference point. */
    data class Short(val text: String) : Reading

    /**
     * A short form given with the place it is near, as section 12.1 writes it:
     * `-98NM9 near Old Toronto, Ontario, Canada`. The place has to be looked up
     * before the five characters mean anything.
     */
    data class Anchored(val short: String, val reference: String) : Reading

    /** A location written in another system, read by that system's own rules. */
    data class Other(val found: OtherFormat) : Reading

    /** A point written some other way: decimal degrees, degrees and minutes, a geo URI. */
    data class At(val point: Point) : Reading

    /** A well-formed code from the reserved range: not wrong, and not a place. */
    data class Reserved(val code: String) : Reading

    /** Nothing this reader understands, with the library's reason when it gave one. */
    data class Unread(val reason: String?) : Reading
}

/** Read one piece of text. */
fun read(text: String): Reading {
    val given = text.trim()
    if (given.isEmpty()) return Reading.Unread(null)

    linked(given)?.let { return it }
    readOther(given)?.let { return Reading.Other(it) }
    if (given.startsWith("geo:", ignoreCase = true)) return geo(given)
    ANCHORED.matchEntire(given)?.let { found ->
        return Reading.Anchored("-" + found.groupValues[1].uppercase(), found.groupValues[2].trim())
    }
    if (SHORT.matches(given)) return Reading.Short(given)
    decimal(given)?.let { return it }

    val verdict = GPC.Validate(given)
    return when (verdict.Kind) {
        CodeClass.GEOMETRIC -> Reading.Code(GPC.Normalise(given)[0])
        CodeClass.RESERVED -> Reading.Reserved(GPC.Normalise(given)[0])
        else -> degrees(given) ?: Reading.Unread(verdict.Reason)
    }
}

/**
 * A short form, read against the place it is near.
 *
 * Exact within about four kilometres of the true cell, which is the half cell
 * the specification recovers against. Beyond that it names a different place
 * with the same five characters, which is why the full code is what gets shared.
 */
fun recover(short: String, near: Point): Selection? = runCatching {
    selectionOf(GPC.RecoverShort(short, near.latitude, near.longitude), Source.CODE)
}.getOrNull()

/** A link to a place on the site, read for its code and its directions. */
private fun linked(given: String): Reading? {
    if (!given.contains("://") || !given.contains('?')) return null
    val code = codeIn(given) ?: return null
    return when (val inner = read(code)) {
        is Reading.Code -> inner.copy(note = noteIn(given))
        is Reading.Reserved, is Reading.Unread -> inner
        else -> Reading.Unread("GPC_LINK")
    }
}

private fun geo(given: String): Reading = try {
    val point = GPC.FromGeoURI(given)
    Reading.At(Point(point.Latitude, point.Longitude))
} catch (failure: GPCException) {
    Reading.Unread(failure.reason)
} catch (failure: IllegalArgumentException) {
    Reading.Unread(failure.message)
}

private fun decimal(given: String): Reading? {
    val match = DECIMAL.matchEntire(given) ?: return null
    val latitude = match.groupValues[1].toDouble()
    val longitude = match.groupValues[2].toDouble()
    val check = GPC.IsValid(latitude, longitude)
    return if (check.IsValid) Reading.At(Point(latitude, longitude)) else Reading.Unread(check.Message)
}

private fun degrees(given: String): Reading? = try {
    val point = GPC.FromDMS(given)
    Reading.At(Point(point.Latitude, point.Longitude))
} catch (failure: IllegalArgumentException) {
    null
}

/**
 * Five symbols, their dash if it was kept, then "near" and a place. The dash is
 * the short form's own marker, but a line retyped by hand often loses it, and
 * "near" says what the five characters are just as plainly.
 */
private val ANCHORED = Regex("^-?\\s*([0-9A-Za-z]{5})\\s+near\\s+(\\S.*)$", RegexOption.IGNORE_CASE)

/** A hyphen, then five symbols: the printed second group on its own. */
private val SHORT = Regex("^-\\s*[0-9A-Za-z]{5}$")

/** Two decimal numbers, latitude first, separated by a comma, spaces or both. */
private val DECIMAL = Regex("^([+-]?\\d{1,3}(?:\\.\\d+)?)\\s*(?:,\\s*|\\s+)([+-]?\\d{1,3}(?:\\.\\d+)?)$")
