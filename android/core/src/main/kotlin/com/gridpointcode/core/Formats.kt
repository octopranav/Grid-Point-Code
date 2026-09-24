package com.gridpointcode.core

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/*
 * Locations written in other systems, read and named.
 *
 * The one part of this repository that names other systems, by the author's
 * decision: turning somebody's existing location into a code is the strongest
 * reason to use one, and a reader is owed the name of what they gave. Each
 * format is read by its own published rules, and the reader is told how much
 * the source actually said: a code for the centre of a 14 m square is not a
 * doorway, however many characters it has.
 */

/** The other systems this app reads, as their owners name them. */
enum class Format {
    PLUS_CODE,
    DIGIPIN,
    GEOHASH,
    GOOGLE_MAPS,
    APPLE_MAPS,
    OPENSTREETMAP,
    BING_MAPS,
    WAZE,
    WHAT3WORDS,
}

/** What a location in another system came to. */
sealed interface OtherFormat {
    val format: Format

    /**
     * A point.
     *
     * @property metres the longer side of the area the source named, which is
     *   how precise it was, whatever this app's code for its centre might claim
     * @property viewCentre the source gave the middle of a map view rather than
     *   a place anybody pinned
     */
    data class At(
        override val format: Format,
        val point: Point,
        val metres: Double,
        val viewCentre: Boolean = false,
    ) : OtherFormat

    /** A short Plus Code, which names somewhere only against a place near it. */
    data class Near(override val format: Format, val code: String, val locality: String?) : OtherFormat

    /** Recognised, and turned into a place only by its owner's service, which this app does not use. */
    data class Closed(override val format: Format) : OtherFormat

    /** A short link, which says where it goes only when it is followed. */
    data class Unfollowed(override val format: Format) : OtherFormat
}

/** Read text as a location in another system, or null when it is none of them. */
fun readOther(text: String): OtherFormat? {
    val given = text.trim()
    if (given.isEmpty()) return null
    if (given.startsWith("///")) return OtherFormat.Closed(Format.WHAT3WORDS).takeIf { WORDS.matches(given.drop(3)) }
    link(given)?.let { return it }
    labelled(given)?.let { return it }
    plusCode(given)?.let { return it }
    return spacedDigipin(given)
}

/**
 * The other reading of ten characters that are a code in this system and also
 * a DIGIPIN, which since May 2026 is written with no separators. This system's
 * reading comes first; this one is offered beside it, because a reader in India
 * may well have meant it.
 */
fun otherReading(text: String): OtherFormat.At? {
    val bare = text.trim().uppercase()
    if (!DIGIPIN_BARE.matches(bare)) return null
    return digipin(bare)
}

/** A short Plus Code read against a place near it: within about 50 km for the usual four-character trim. */
fun recoverNear(code: String, near: Point): OtherFormat.At? {
    val short = code.trim().uppercase()
    val separator = short.indexOf(PLUS_SEPARATOR)
    if (!plusValid(short) || separator >= PLUS_SEPARATOR_AT) return null
    val trimmed = PLUS_SEPARATOR_AT - separator
    val resolution = 20.0.pow(2 - trimmed / 2)
    val half = resolution / 2
    val latitude = near.latitude.coerceIn(-90.0, 90.0)
    val longitude = plusLongitude(near.longitude)
    val area = plusArea(plusPrefix(latitude, longitude, trimmed) + short) ?: return null
    var centreLatitude = (area.south + area.north) / 2
    var centreLongitude = (area.west + area.east) / 2
    if (latitude + half < centreLatitude && centreLatitude - resolution >= -90) centreLatitude -= resolution
    else if (latitude - half > centreLatitude && centreLatitude + resolution <= 90) centreLatitude += resolution
    if (longitude + half < centreLongitude) centreLongitude -= resolution
    else if (longitude - half > centreLongitude) centreLongitude += resolution
    val point = Point(centreLatitude, plusLongitude(centreLongitude))
    return OtherFormat.At(Format.PLUS_CODE, point, area.metres())
}

// Plus Codes.

private const val PLUS_ALPHABET = "23456789CFGHJMPQRVWX"
private const val PLUS_SEPARATOR = '+'
private const val PLUS_SEPARATOR_AT = 8
private const val PLUS_PADDING = '0'
private const val PLUS_PAIRS = 10
private const val PLUS_MOST_DIGITS = 15
private val PLUS_RESOLUTIONS = doubleArrayOf(20.0, 1.0, 0.05, 0.0025, 0.000125)

/** A code, full or short, and whatever follows it, which for a short code is the town it is near. */
private fun plusCode(given: String): OtherFormat? {
    val token = given.substringBefore(' ').trimEnd(',')
    if (!token.contains(PLUS_SEPARATOR)) return null
    val code = token.uppercase()
    if (!plusValid(code)) return null
    val locality = given.substringAfter(' ', "").trim().trimStart(',').trim().ifEmpty { null }
    return if (code.indexOf(PLUS_SEPARATOR) == PLUS_SEPARATOR_AT) {
        plusArea(code)?.let { OtherFormat.At(Format.PLUS_CODE, it.centre(), it.metres()) }
    } else {
        OtherFormat.Near(Format.PLUS_CODE, code, locality)
    }
}

/** The format's own validity rules: one separator, at an even place no later than eighth, padding only in full codes. */
private fun plusValid(code: String): Boolean {
    if (code.length < 2) return false
    val separator = code.indexOf(PLUS_SEPARATOR)
    if (separator == -1 || separator != code.lastIndexOf(PLUS_SEPARATOR)) return false
    if (separator > PLUS_SEPARATOR_AT || separator % 2 == 1) return false
    val padding = code.indexOf(PLUS_PADDING)
    if (padding >= 0) {
        if (separator < PLUS_SEPARATOR_AT || padding == 0 || padding > separator) return false
        val run = code.substring(padding, separator)
        if (run.any { it != PLUS_PADDING } || run.length % 2 == 1) return false
        if (!code.endsWith(PLUS_SEPARATOR)) return false
    }
    if (code.length - separator - 1 == 1) return false
    if (code.any { it != PLUS_SEPARATOR && it != PLUS_PADDING && it !in PLUS_ALPHABET }) return false
    if (separator == PLUS_SEPARATOR_AT) {
        // A full code's first two symbols must name a latitude and longitude on the Earth.
        if (PLUS_ALPHABET.indexOf(code[0]) * 20 >= 180 || PLUS_ALPHABET.indexOf(code[1]) * 20 >= 360) return false
    }
    return true
}

/** The area a full code names: pairs of symbols to ten, then a four by five grid for each symbol after. */
private fun plusArea(code: String): Area? {
    val digits = code.filter { it != PLUS_SEPARATOR && it != PLUS_PADDING }.take(PLUS_MOST_DIGITS)
    if (digits.length < 2 || digits.any { it !in PLUS_ALPHABET }) return null
    var south = -90.0
    var west = -180.0
    var tall = 0.0
    var wide = 0.0
    var i = 0
    while (i < min(digits.length, PLUS_PAIRS) - 1) {
        val resolution = PLUS_RESOLUTIONS[i / 2]
        south += PLUS_ALPHABET.indexOf(digits[i]) * resolution
        west += PLUS_ALPHABET.indexOf(digits[i + 1]) * resolution
        tall = resolution
        wide = resolution
        i += 2
    }
    for (j in PLUS_PAIRS until digits.length) {
        val index = PLUS_ALPHABET.indexOf(digits[j])
        tall /= 5
        wide /= 4
        south += (index / 4) * tall
        west += (index % 4) * wide
    }
    return Area(south, west, south + tall, west + wide)
}

/** The first symbols of a full code for a point, as many as a short code left out. */
private fun plusPrefix(latitude: Double, longitude: Double, count: Int): String {
    var lat = latitude + 90
    var lng = longitude + 180
    val out = StringBuilder()
    for (pair in 0 until count / 2) {
        val resolution = PLUS_RESOLUTIONS[pair]
        val latDigit = floor(lat / resolution).toInt().coerceIn(0, 19)
        val lngDigit = floor(lng / resolution).toInt().coerceIn(0, 19)
        lat -= latDigit * resolution
        lng -= lngDigit * resolution
        out.append(PLUS_ALPHABET[latDigit]).append(PLUS_ALPHABET[lngDigit])
    }
    return out.toString()
}

private fun plusLongitude(longitude: Double): Double {
    var lng = longitude
    while (lng < -180) lng += 360
    while (lng >= 180) lng -= 360
    return lng
}

// DIGIPIN.

/** India Post's grid, row by row from the north. */
private const val DIGIPIN_GRID = "FC98J327K456LMPT"
private val DIGIPIN_BARE = Regex("[23456789CJKLMPFT]{10}")

/** Three, three and four symbols with a space or hyphen between: the written form before May 2026, and only a DIGIPIN. */
private val DIGIPIN_SPACED = Regex("([23456789CJKLMPFT]{3})[ -]([23456789CJKLMPFT]{3})[ -]([23456789CJKLMPFT]{4})", RegexOption.IGNORE_CASE)

private fun spacedDigipin(given: String): OtherFormat.At? {
    val found = DIGIPIN_SPACED.matchEntire(given) ?: return null
    return digipin(found.groupValues.drop(1).joinToString("").uppercase())
}

/** India Post's decoding: ten levels of a four by four grid over 2.5 to 38.5 N and 63.5 to 99.5 E, then the centre. */
private fun digipin(pin: String): OtherFormat.At? {
    var south = 2.5
    var north = 38.5
    var west = 63.5
    var east = 99.5
    for (symbol in pin) {
        val at = DIGIPIN_GRID.indexOf(symbol)
        if (at < 0) return null
        val row = at / 4
        val column = at % 4
        val tall = (north - south) / 4
        val wide = (east - west) / 4
        north -= tall * row
        south = north - tall
        west += wide * column
        east = west + wide
    }
    val area = Area(south, west, north, east)
    return OtherFormat.At(Format.DIGIPIN, area.centre(), area.metres())
}

// Geohash.

private const val GEOHASH_ALPHABET = "0123456789bcdefghjkmnpqrstuvwxyz"

/**
 * Only when it says it is one: a bare geohash is a word as often as not, and
 * reading "berry" as a place in the Caribbean would be the wrong kind of clever.
 */
private fun geohash(hash: String): OtherFormat.At? {
    val clean = hash.trim().lowercase()
    if (clean.isEmpty() || clean.length > 12 || clean.any { it !in GEOHASH_ALPHABET }) return null
    var south = -90.0
    var north = 90.0
    var west = -180.0
    var east = 180.0
    var longitude = true
    for (symbol in clean) {
        val bits = GEOHASH_ALPHABET.indexOf(symbol)
        for (mask in intArrayOf(16, 8, 4, 2, 1)) {
            if (longitude) {
                val middle = (west + east) / 2
                if ((bits and mask) != 0) west = middle else east = middle
            } else {
                val middle = (south + north) / 2
                if ((bits and mask) != 0) south = middle else north = middle
            }
            longitude = !longitude
        }
    }
    val area = Area(south, west, north, east)
    return OtherFormat.At(Format.GEOHASH, area.centre(), area.metres())
}

// Labelled text and links.

/** A format named before its value: `DIGIPIN 4T396F42L7`, `geohash:u4pruydqqvj`. */
private fun labelled(given: String): OtherFormat? {
    val found = LABELLED.matchEntire(given) ?: return null
    val value = found.groupValues[2]
    return when (found.groupValues[1].lowercase()) {
        "digipin" -> value.replace(Regex("[ -]"), "").uppercase().takeIf { DIGIPIN_BARE.matches(it) }?.let(::digipin)
        "geohash" -> geohash(value)
        else -> null
    }
}

private val LABELLED = Regex("(digipin|geohash)\\s*:?\\s*(\\S.*)", RegexOption.IGNORE_CASE)

/** Three words and two dots, the form the owner prints after three slashes. */
private val WORDS = Regex("\\p{L}+\\.\\p{L}+\\.\\p{L}+")

private val BARE_HOST = Regex(
    "^(www\\.)?(maps\\.google\\.|google\\.[a-z.]+/maps|maps\\.app\\.goo\\.gl|goo\\.gl/maps|maps\\.apple\\.com|" +
        "openstreetmap\\.org|osm\\.org|bing\\.com/maps|waze\\.com|what3words\\.com|w3w\\.co|geohash\\.org|plus\\.codes)",
    RegexOption.IGNORE_CASE,
)

/** A link from a map service, read for the place in it. */
private fun link(given: String): OtherFormat? {
    val address = when {
        given.contains("://") -> given
        BARE_HOST.containsMatchIn(given) -> "https://$given"
        else -> return null
    }
    val uri = runCatching { URI(address.replace(" ", "%20")) }.getOrNull() ?: return null
    val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
    val path = uri.rawPath.orEmpty()
    val query = parameters(uri.rawQuery)
    return when {
        host == "maps.app.goo.gl" || (host == "goo.gl" && path.startsWith("/maps")) ->
            OtherFormat.Unfollowed(Format.GOOGLE_MAPS)
        GOOGLE_HOST.matches(host) && (path.startsWith("/maps") || host.startsWith("maps.")) -> google(path, query)
        host == "maps.apple.com" || host == "maps.apple" ->
            firstPair(query, "coordinate", "ll", "q", "daddr", "sll")?.let { it.copy(format = Format.APPLE_MAPS) }
        host == "openstreetmap.org" || host == "osm.org" -> openStreetMap(query, uri.rawFragment)
        host == "bing.com" && path.startsWith("/maps") -> bing(query)
        host.endsWith("waze.com") -> waze(query)
        host == "what3words.com" || host == "w3w.co" ->
            OtherFormat.Closed(Format.WHAT3WORDS).takeIf { WORDS.matches(decode(path.trim('/'))) }
        host == "geohash.org" -> geohash(decode(path.trim('/')).substringBefore('?'))
        // A plus sign in a path is itself, not a space, and in this path it is the code's separator.
        host == "plus.codes" -> plusCode(decode(path.trim('/').replace("+", "%2B")))
        else -> null
    }
}

private val GOOGLE_HOST = Regex("(maps\\.)?google\\.[a-z]{2,3}(\\.[a-z]{2})?")

/**
 * A place's own coordinates first, which a shared place carries as `!3d` and
 * `!4d`; then a query or destination written as coordinates; then the map's
 * centre after `@`, which is only where the view was when it was shared.
 */
private fun google(path: String, query: Map<String, String>): OtherFormat.At? {
    PLACE_DATA.find(path)?.let { found ->
        return pair(found.groupValues[1], found.groupValues[2], Format.GOOGLE_MAPS)
    }
    firstPair(query, "q", "query", "ll", "destination", "daddr", "center")?.let { return it.copy(format = Format.GOOGLE_MAPS) }
    PLACE_PATH.find(decode(path))?.let { found ->
        return pair(found.groupValues[1], found.groupValues[2], Format.GOOGLE_MAPS)
    }
    VIEW_CENTRE.find(path)?.let { found ->
        return pair(found.groupValues[1], found.groupValues[2], Format.GOOGLE_MAPS)?.copy(viewCentre = true)
    }
    return null
}

private val PLACE_DATA = Regex("!3d(-?\\d+(?:\\.\\d+)?)!4d(-?\\d+(?:\\.\\d+)?)")
private val PLACE_PATH = Regex("/(?:place|search|dir)/(-?\\d+(?:\\.\\d+)?)[, ]+\\+?(-?\\d+(?:\\.\\d+)?)")
private val VIEW_CENTRE = Regex("@(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)")

/** A marker's coordinates when the link has one, otherwise the centre of the view in its fragment. */
private fun openStreetMap(query: Map<String, String>, fragment: String?): OtherFormat.At? {
    val latitude = query["mlat"]
    val longitude = query["mlon"]
    if (latitude != null && longitude != null) return pair(latitude, longitude, Format.OPENSTREETMAP)
    val view = fragment?.let { OSM_VIEW.find(it) } ?: return null
    return pair(view.groupValues[1], view.groupValues[2], Format.OPENSTREETMAP)?.copy(viewCentre = true)
}

private val OSM_VIEW = Regex("map=\\d+(?:\\.\\d+)?/(-?\\d+(?:\\.\\d+)?)/(-?\\d+(?:\\.\\d+)?)")

/** A pinned point in `sp`, otherwise the view's centre in `cp`. */
private fun bing(query: Map<String, String>): OtherFormat.At? {
    query["sp"]?.let { value ->
        BING_POINT.find(value)?.let { return pair(it.groupValues[1], it.groupValues[2], Format.BING_MAPS) }
    }
    val centre = query["cp"]?.split('~') ?: return null
    if (centre.size != 2) return null
    return pair(centre[0], centre[1], Format.BING_MAPS)?.copy(viewCentre = true)
}

private val BING_POINT = Regex("point\\.(-?\\d+(?:\\.\\d+)?)_(-?\\d+(?:\\.\\d+)?)")

private fun waze(query: Map<String, String>): OtherFormat.At? {
    firstPair(query, "ll", "latlng")?.let { return it.copy(format = Format.WAZE) }
    val to = query["to"]?.removePrefix("ll.") ?: return null
    return coordinatePair(to)?.copy(format = Format.WAZE)
}

/** The first of the named parameters that holds a pair of coordinates. */
private fun firstPair(query: Map<String, String>, vararg names: String): OtherFormat.At? =
    names.firstNotNullOfOrNull { name -> query[name]?.let(::coordinatePair) }

private fun coordinatePair(value: String): OtherFormat.At? {
    val found = PAIR.matchEntire(value.trim()) ?: return null
    return pair(found.groupValues[1], found.groupValues[2], Format.GOOGLE_MAPS)
}

private val PAIR = Regex("(?:loc:)?\\s*([+-]?\\d{1,3}(?:\\.\\d+)?)\\s*,\\s*\\+?([+-]?\\d{1,3}(?:\\.\\d+)?)")

/**
 * Two numbers as a point, with the precision their decimals admit: a link that
 * says 43.65, -79.38 knows the place to about a kilometre, and the code for it
 * should not be read as a door.
 */
private fun pair(latitude: String, longitude: String, format: Format): OtherFormat.At? {
    val lat = latitude.toDoubleOrNull() ?: return null
    val lng = longitude.toDoubleOrNull() ?: return null
    if (lat < -90 || lat > 90 || lng < -180 || lng > 180) return null
    val decimals = min(latitude.substringAfter('.', "").length, longitude.substringAfter('.', "").length)
    return OtherFormat.At(format, Point(lat, lng), METRES_PER_DEGREE * 10.0.pow(-decimals))
}

private fun parameters(raw: String?): Map<String, String> =
    raw.orEmpty().split('&').filter { it.isNotEmpty() }.associate { part ->
        decode(part.substringBefore('=')) to decode(part.substringAfter('=', ""))
    }

private fun decode(value: String): String =
    runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8) }.getOrDefault(value)

// Shared.

private const val METRES_PER_DEGREE = 111_320.0

private data class Area(val south: Double, val west: Double, val north: Double, val east: Double) {
    fun centre() = Point((south + north) / 2, (west + east) / 2)

    /** The longer side on the ground, in metres. */
    fun metres(): Double {
        val middle = Math.toRadians((south + north) / 2)
        return max((north - south) * METRES_PER_DEGREE, (east - west) * METRES_PER_DEGREE * cos(middle))
    }
}
