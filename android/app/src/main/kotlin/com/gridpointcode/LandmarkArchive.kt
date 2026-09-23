package com.gridpointcode

import com.gridpointcode.core.Anchor
import com.gridpointcode.core.Landmark
import com.gridpointcode.core.LandmarkKind
import com.gridpointcode.core.Point
import com.gridpointcode.core.SITE
import com.gridpointcode.core.anchorsFor
import com.gridpointcode.core.shardsFor
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
 * one, a few kilobytes. A shard once read is kept while the app runs: the
 * archive does not change between deployments, and a reader nudging a point
 * about asks for the same shard again and again. Which shards, and which of
 * their landmarks qualify, are decided in `:core`.
 */
class LandmarkArchive(private val site: String = SITE) {

    /** What a lookup came to. */
    sealed interface Near {
        /** Every landmark close enough, nearest first. Empty in open country, which is an answer. */
        data class Found(val anchors: List<Anchor>) : Near

        /** Part of the archive could not be read, so a list would be missing places without saying so. */
        data object Unreachable : Near
    }

    private val lock = Mutex()
    private var level: Int? = null
    private val shards = HashMap<String, List<Landmark>>()

    suspend fun near(point: Point): Near = withContext(Dispatchers.IO) {
        val level = level() ?: return@withContext Near.Unreachable
        val held = coroutineScope {
            shardsFor(point, level).map { name -> async { shard(name) } }.awaitAll()
        }
        if (held.any { it == null }) return@withContext Near.Unreachable
        Near.Found(anchorsFor(point, held.flatMap { it.orEmpty() }))
    }

    /**
     * The level the shards were cut at, which is a property of the archive, not
     * of this app. Assuming it would turn a rebuilt archive into a world of
     * misses, and a miss reads as open country rather than a broken lookup.
     */
    private suspend fun level(): Int? = lock.withLock {
        level ?: fetch("$site/landmarks/manifest.json")?.let { body ->
            runCatching { JSONObject(body).getInt("level") }.getOrNull()
        }?.also { level = it }
    }

    /**
     * One shard, or null when it could not be read. A shard that does not exist
     * is ocean, most of the planet, and is remembered as empty; a failed read is
     * not remembered, so the next lookup tries again.
     */
    private suspend fun shard(name: String): List<Landmark>? {
        lock.withLock { shards[name] }?.let { return it }
        val connection = open("$site/landmarks/$name.json")
        val read = try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> parse(connection.inputStream.bufferedReader().use { it.readText() })
                HttpURLConnection.HTTP_NOT_FOUND -> emptyList()
                else -> null
            }
        } catch (failure: java.io.IOException) {
            null
        } finally {
            connection.disconnect()
        }
        if (read != null) lock.withLock { shards[name] = read }
        return read
    }

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

    private fun fetch(address: String): String? = runCatching {
        val connection = open(address)
        try {
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
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
