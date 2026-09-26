package com.gridpointcode.core

import ca.pranavpatel.algo.gridpointcode.GPC
import java.text.Normalizer

/*
 * Finding a place by its name, from the website's name index.
 *
 * The index is one file of every name the site knows, sorted, beside a sparse
 * table of every 512th line and the byte where it starts. Every name beginning
 * with what somebody typed sits in one run of the file, so a search is the table
 * to find the block and one range read to fetch it. Nothing here asks a service
 * anything: both files are static, on the same host as the site, and a hit
 * carries its code, which is the coordinate.
 *
 * This reads the files the site's own search reads (web/src/lib/search.ts), and
 * folds a name exactly as the builder sorted it (web/scripts/build-names.mjs).
 */

/** A place the index knows, as the website lists it. */
data class Named(
    /** As it is written locally: `Trá Mhór`. */
    val name: String,
    /** Ten characters. The coordinate, not a key to one. */
    val code: String,
    /** Where it is, for telling two places of the same name apart. */
    val region: String,
)

/**
 * The sparse table: the folded name at every 512th line of the file, and the
 * byte where that line starts.
 *
 * @property bytes the length of the whole file, which is where the last block ends
 */
class NameTable(
    val bytes: Long,
    val regions: List<String>,
    val keys: List<String>,
    val starts: LongArray,
)

/** Bytes `from` until `until` of the sorted file, or null when they could not be read. */
fun interface NameFile {
    fun read(from: Long, until: Long): String?
}

/** The fewest folded characters worth a search. One letter matches a sixth of the world. */
const val NAME_SHORTEST = 2

/**
 * A search reads at most this many blocks. A prefix matching more than that is
 * so short that the first answers are as good as any others.
 */
private const val MOST_BLOCKS = 8

/**
 * What a name is reduced to for searching.
 *
 * **This must agree exactly with the builder**, which sorted the file by it. A
 * difference is not a wrong answer but a binary search landing in the wrong part
 * of a third of a gigabyte and reporting the place missing. The order matters as
 * much as the steps: lowercase first, then decompose, so a letterlike symbol that
 * decomposes to a capital is dropped, as the builder drops it.
 */
fun fold(name: String): String =
    Normalizer.normalize(name.lowercase(), Normalizer.Form.NFKD)
        .replace(COMBINING, "")
        .replace(NOT_LETTER_OR_DIGIT, " ")
        .trim()

private val COMBINING = Regex("[\\u0300-\\u036f]")
private val NOT_LETTER_OR_DIGIT = Regex("[^a-z0-9]+")

/**
 * Whether text should be looked up by name: nothing the reader already
 * understands as a code, a point or a link, and long enough to narrow.
 */
fun isName(text: String): Boolean = read(text) is Reading.Unread && fold(text).length >= NAME_SHORTEST

/**
 * Places whose name begins with [query], in the file's order: alphabetical, and
 * within one name the largest place first.
 *
 * Reads on past the block the query lands in while the lines still match,
 * because a run can cross a block boundary: `london` does not stop being an
 * answer because the 512th line fell in the middle of it.
 *
 * Null when the file could not be read at all, which is not the same answer as
 * nothing being called that.
 */
fun findNamed(query: String, table: NameTable, file: NameFile, most: Int = 12): List<Named>? =
    matchingLines(query, table, file, most)?.mapNotNull { parse(it, table.regions) }

/** One searchable file of the index: its sparse table, and a way to read it. */
class NameSource(val table: NameTable, val file: NameFile)

/**
 * Places whose name begins with [query] across [sources], in the index's order,
 * as though they were one file. Each source is a country's lines of the same
 * sorted index, so its matches merge back into the order the whole file has.
 * A source that cannot be read gives nothing, and the others still answer.
 */
fun findNamedAcross(query: String, sources: List<NameSource>, most: Int = 12): List<Named> =
    sources
        .flatMap { source -> matchingLines(query, source.table, source.file, most).orEmpty().map { it to source.table.regions } }
        .sortedBy { it.first }
        .take(most)
        .mapNotNull { (line, regions) -> parse(line, regions) }

/** The lines of one file whose name begins with [query], in its order; null when it could not be read at all. */
private fun matchingLines(query: String, table: NameTable, file: NameFile, most: Int): List<String>? {
    val folded = fold(query)
    if (folded.length < NAME_SHORTEST) return emptyList()

    val hits = mutableListOf<String>()
    var block = blockFor(table, folded)
    repeat(MOST_BLOCKS) {
        if (block >= table.keys.size) return hits
        val from = table.starts[block]
        val until = if (block + 1 < table.keys.size) table.starts[block + 1] else table.bytes
        val body = file.read(from, until) ?: return hits.ifEmpty { null }

        var matched = false
        for (line in body.split('\n')) {
            if (line.isEmpty()) continue
            val key = line.substringBefore('\t')
            if (key < folded) continue
            // At or after the query and not beginning with it: past the run,
            // and nothing later in the file can match.
            if (!key.startsWith(folded)) return hits
            matched = true
            if (line.count { it == '\t' } >= 4) hits += line
            if (hits.size >= most) return hits
        }
        if (!matched && hits.isNotEmpty()) return hits
        block += 1
    }
    return hits
}

/** How many lines apart the marks of a sparse table fall, as the builder spaces them. */
const val NAME_STRIDE = 512

/**
 * The sparse table of a file, built as its lines are written: the name at every
 * [stride]th line and the byte where that line starts, counted in UTF-8 as the
 * file is written, so a kept pack is searched exactly as the site's file is.
 */
class NameMarks(private val stride: Int = NAME_STRIDE) {
    private val keys = mutableListOf<String>()
    private val starts = mutableListOf<Long>()

    var bytes = 0L
        private set
    var lines = 0
        private set

    fun add(line: String) {
        if (lines % stride == 0) {
            keys += line.substringBefore('\t')
            starts += bytes
        }
        bytes += line.toByteArray(Charsets.UTF_8).size + 1
        lines += 1
    }

    fun table(regions: List<String>): NameTable = NameTable(bytes, regions, keys.toList(), starts.toLongArray())
}

/**
 * Where a run of names beginning with [folded] can start: the block of the last
 * mark strictly before it.
 *
 * Not the last mark at or before it. Marks fall every 512th line whatever the
 * name, so about a quarter of them land partway through a run of one name, and
 * a search that began at such a mark skipped the lines before it, which is
 * where the largest places are: `al marj` answered with a village in Syria and
 * never reached the city in Libya. Starting one block earlier costs one more
 * read only when a mark's name is exactly the query.
 */
private fun blockFor(table: NameTable, folded: String): Int {
    var low = 0
    var high = table.keys.size - 1
    var at = 0
    while (low <= high) {
        val middle = (low + high) ushr 1
        if (table.keys[middle] < folded) {
            at = middle
            low = middle + 1
        } else {
            high = middle - 1
        }
    }
    return at
}

/**
 * One line of the file: `folded, importance, name, code, region`, tab separated.
 * Importance did its work in the sort and is read past here.
 */
private fun parse(line: String, regions: List<String>): Named? {
    val parts = line.split('\t')
    if (parts.size < 5) return null
    return Named(
        name = parts[2],
        code = parts[3],
        region = parts[4].toIntOrNull()?.let { regions.getOrNull(it) } ?: "",
    )
}

/**
 * Places on the device whose name begins with [query], for searching with no
 * connection: the landmarks of the areas kept and of places already looked at.
 *
 * Ordered as the index orders a name, alphabetically by what it folds to, with
 * populated places ahead of buildings and hills within one name, since the
 * archive carries no population to rank by. Only names unique within their
 * region are in the archive, so a town with a namesake in the same province
 * cannot be found this way.
 */
fun findLocal(query: String, landmarks: List<Landmark>, most: Int = 12): List<Named> {
    val folded = fold(query)
    if (folded.length < NAME_SHORTEST) return emptyList()
    return landmarks.asSequence()
        .map { fold(it.name) to it }
        .filter { (key, _) -> key.startsWith(folded) }
        .sortedWith(compareBy({ it.first }, { if (it.second.kind == LandmarkKind.PLACE) 0 else 1 }, { it.second.name }))
        .distinctBy { (_, landmark) -> landmark.name + "\n" + landmark.region }
        .mapNotNull { (_, landmark) ->
            runCatching { GPC.Encode(landmark.latitude, landmark.longitude, false) }.getOrNull()
                ?.let { Named(landmark.name, it, landmark.region) }
        }
        .take(most)
        .toList()
}
