package com.gridpointcode

import ca.pranavpatel.algo.gridpointcode.GPC
import com.gridpointcode.core.Anchor
import com.gridpointcode.core.Landmark
import com.gridpointcode.core.LandmarkKind
import com.gridpointcode.core.Named
import com.gridpointcode.core.Point
import com.gridpointcode.core.SITE
import com.gridpointcode.core.anchorsFor
import com.gridpointcode.core.areaFor
import com.gridpointcode.core.landmarkFor
import com.gridpointcode.core.shardsFor
import com.gridpointcode.core.shardsIn
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The website's landmark archive: 6.5 million places, each named uniquely
 * within its region, in shards keyed by the cell they fall in.
 *
 * A place needs the one to four shards its recovery box reaches into, usually
 * one, a few kilobytes. A shard once read is held while the app runs: a reader
 * nudging a point about asks for the same shard again and again. An area the
 * reader kept is read from the device, with no network, for as long as it
 * comes from the archive the site is serving; which shards, and which of their
 * landmarks qualify, are decided in `:core`.
 */
class LandmarkArchive(private val kept: KeptLandmarks, private val site: String = SITE) {

    /** What a lookup came to. */
    sealed interface Near {
        /**
         * Every landmark close enough, nearest first. Empty in open country,
         * which is an answer.
         *
         * @property partial part of the recovery box lies outside what could be
         *   read, the edge of an area kept offline, so places there are missing
         *   and the reader is told so
         */
        data class Found(val anchors: List<Anchor>, val partial: Boolean = false) : Near

        /** None of the archive around the point could be read. */
        data object Unreachable : Near
    }

    /** What looking up one landmark came to. */
    sealed interface Held {
        data class Found(val landmark: Landmark) : Held

        /** Not in the archive: its name is not unique within its region. */
        data object Missing : Held

        data object Unreachable : Held
    }

    /** What keeping an area came to. */
    sealed interface Keeping {
        data class Kept(val area: KeptLandmarks.Area) : Keeping

        /** Part of the area could not be fetched, so none of it was recorded as kept. */
        data object Failed : Keeping
    }

    private val lock = Mutex()

    /** The archive the site is serving, once it has been asked. Null offline. */
    private var serving: KeptLandmarks.Source? = null
    private val shards = HashMap<String, List<Landmark>>()

    suspend fun near(point: Point): Near = withContext(Dispatchers.IO) {
        val level = level() ?: return@withContext Near.Unreachable
        val held = coroutineScope {
            shardsFor(point, level).map { name -> async { shard(name) } }.awaitAll()
        }
        if (held.all { it == null }) return@withContext Near.Unreachable
        Near.Found(anchorsFor(point, held.flatMap { it.orEmpty() }), partial = held.any { it == null })
    }

    /**
     * The archive's entry for a place from the name index. The index row carries
     * the place's code, and the code's cell at the archive's level is the shard
     * it lives in, so one shard is all it takes.
     */
    suspend fun landmark(place: Named): Held = withContext(Dispatchers.IO) {
        val level = level() ?: return@withContext Held.Unreachable
        val shard = runCatching { GPC.Cell(place.code, level) }.getOrNull()
            ?: return@withContext Held.Missing
        val held = shard(shard) ?: return@withContext Held.Unreachable
        landmarkFor(place, held)?.let { Held.Found(it) } ?: Held.Missing
    }

    /** The area around a point, and whether it is kept, or null while the archive is not known. */
    suspend fun area(point: Point): Pair<String, KeptLandmarks.Area?>? = withContext(Dispatchers.IO) {
        val level = level() ?: return@withContext null
        val cell = areaFor(point, level) ?: return@withContext null
        cell to kept.areas().firstOrNull { it.cell == cell }
    }

    suspend fun keptAreas(): List<KeptLandmarks.Area> = withContext(Dispatchers.IO) { kept.areas() }

    /**
     * Fetch every shard of the area around a point and keep them on the device.
     * All of it or none: an area recorded as kept with a shard missing would
     * fail offline exactly where it was promised not to.
     */
    suspend fun keep(point: Point): Keeping = withContext(Dispatchers.IO) {
        val source = lock.withLock { serving } ?: fetchSource()?.also { found -> lock.withLock { serving = found } }
            ?: return@withContext Keeping.Failed
        val cell = areaFor(point, source.level) ?: return@withContext Keeping.Failed
        val fetched = coroutineScope {
            shardsIn(cell).map { name -> async { name to download(name) } }.awaitAll()
        }
        if (fetched.any { (_, body) -> body == null }) return@withContext Keeping.Failed
        Keeping.Kept(kept.keep(source, cell, fetched.associate { (name, body) -> name to body!!.text }))
    }

    /** Give back everything kept. What is held in memory for this session stays. */
    suspend fun forget() = withContext(Dispatchers.IO) { kept.forget() }

    /** Every landmark kept on the device, for reading an anchored line with no connection. */
    suspend fun keptLandmarks(): List<Landmark> = withContext(Dispatchers.IO) {
        kept.everyShard().flatMap { parse(it).orEmpty() }
    }

    /**
     * The level the shards were cut at, which is a property of the archive, not
     * of this app. Assuming it would turn a rebuilt archive into a world of
     * misses, and a miss reads as open country rather than a broken lookup.
     * Offline, the level of the areas kept.
     */
    private suspend fun level(): Int? {
        lock.withLock { serving }?.let { return it.level }
        fetchSource()?.let { found ->
            lock.withLock { serving = found }
            return found.level
        }
        return kept.source()?.level
    }

    /**
     * One shard, or null when it could not be read.
     *
     * Kept on the device and cut from the archive being served, or with nothing
     * served to compare against because there is no connection: read from the
     * device. Otherwise from the site, where a shard that does not exist is
     * ocean, most of the planet, and is held as empty. A failed read is not
     * held, so the next lookup tries again, and falls back to a kept copy from
     * an older archive before giving up: offline, an old landmark is better
     * than none.
     */
    private suspend fun shard(name: String): List<Landmark>? {
        lock.withLock { shards[name] }?.let { return it }
        val current = lock.withLock { serving }
        val local = kept.shard(name)
        val keptSource = kept.source()
        val read = when {
            local != null && (current == null || current.built == keptSource?.built) -> parse(local)
            else -> when (val body = download(name)) {
                null -> local?.let { parse(it) }
                else -> body.text?.let { parse(it) } ?: emptyList()
            }
        }
        if (read != null) lock.withLock { shards[name] = read }
        return read
    }

    /** A shard's file from the site, with a null text for ocean, or null when the read failed. */
    private fun download(name: String): Body? {
        val connection = open("$site/landmarks/$name.json")
        return try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> Body(connection.inputStream.bufferedReader().use { it.readText() })
                HttpURLConnection.HTTP_NOT_FOUND -> Body(null)
                else -> null
            }
        } catch (failure: java.io.IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private class Body(val text: String?)

    /** `{ regions: [...], landmarks: [[name, latitude, longitude, region, kind], ...] }` */
    private fun parse(body: String): List<Landmark>? = runCatching {
        val json = JSONObject(body)
        val regions = json.getJSONArray("regions")
        val rows = json.getJSONArray("landmarks")
        List(rows.length()) { i ->
            val row = rows.getJSONArray(i)
            Landmark(
                name = row.getString(0),
                latitude = row.getDouble(1),
                longitude = row.getDouble(2),
                region = regions.optString(row.getInt(3), ""),
                kind = LandmarkKind.entries.getOrElse(row.getInt(4)) { LandmarkKind.PLACE },
            )
        }
    }.getOrNull()

    private fun fetchSource(): KeptLandmarks.Source? = runCatching {
        val connection = open("$site/landmarks/manifest.json")
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                null
            } else {
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                KeptLandmarks.Source(json.getInt("level"), json.getString("built"))
            }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun open(address: String): HttpURLConnection =
        (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
        }

    private companion object {
        const val TIMEOUT_MS = 10_000
    }
}
