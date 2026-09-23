package com.gridpointcode

import com.gridpointcode.core.NameFile
import com.gridpointcode.core.NameTable
import com.gridpointcode.core.Named
import com.gridpointcode.core.SITE
import com.gridpointcode.core.findNamed
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The website's name index, read over the network the way the site reads it.
 *
 * Two static files on the site's own host. The table, about 14,000 marks and
 * half a megabyte before compression, is fetched on the first search and kept
 * for as long as the app runs. The file itself is a third of a gigabyte, and a
 * search reads one block of it, about 24 kilobytes, by range request. What the
 * host learns is which block: roughly the first letters typed.
 *
 * Where to look and what counts as a match are decided in `:core`, where they
 * are tested; this class only fetches bytes.
 */
class NameIndex(private val site: String = SITE) {

    private val lock = Mutex()
    private var table: NameTable? = null

    /**
     * Places whose name begins with [query], or null when the index could not
     * be reached. A failed fetch of the table is not kept, so the next search
     * tries again once there is a connection.
     */
    suspend fun find(query: String, most: Int = 12): List<Named>? = withContext(Dispatchers.IO) {
        // A table fetched before the site was redeployed describes a file that
        // is no longer there. The first range read notices, by the file's
        // length, and the search is made once more against a fresh table.
        repeat(2) {
            val current = table() ?: return@withContext null
            var stale = false
            val file = NameFile { from, until -> range(from, until, current.bytes) { stale = true } }
            val found = findNamed(query, current, file, most)
            if (!stale) return@withContext found
            lock.withLock { if (table === current) table = null }
        }
        null
    }

    private suspend fun table(): NameTable? = lock.withLock {
        table ?: fetchTable()?.also { table = it }
    }

    private fun fetchTable(): NameTable? = runCatching {
        val connection = open("$site/names/names.index.json")
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                null
            } else {
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val marks = json.getJSONArray("marks")
                val keys = ArrayList<String>(marks.length())
                val starts = LongArray(marks.length())
                for (i in 0 until marks.length()) {
                    val mark = marks.getJSONArray(i)
                    keys += mark.getString(0)
                    starts[i] = mark.getLong(1)
                }
                val regions = json.getJSONArray("regions")
                NameTable(
                    bytes = json.getLong("bytes"),
                    regions = List(regions.length()) { regions.getString(it) },
                    keys = keys,
                    starts = starts,
                )
            }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    /** Bytes `from` until `until` of the file, which should be [length] long. */
    private fun range(from: Long, until: Long, length: Long, onStale: () -> Unit): String? = runCatching {
        val connection = open("$site/names/names.txt")
        connection.setRequestProperty("Range", "bytes=$from-${until - 1}")
        // The table counts the file's own bytes, so they must not arrive
        // compressed, where a byte range would mean something else.
        connection.setRequestProperty("Accept-Encoding", "identity")
        try {
            when {
                // A host that ignored the range would start sending all of it.
                connection.responseCode != HttpURLConnection.HTTP_PARTIAL -> null
                connection.getHeaderField("Content-Range")?.substringAfterLast('/')?.toLongOrNull() != length -> {
                    onStale()
                    null
                }
                else -> connection.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
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
        /** Long enough for a slow connection; a reader waiting longer has given up. */
        const val TIMEOUT_MS = 10_000
    }
}
