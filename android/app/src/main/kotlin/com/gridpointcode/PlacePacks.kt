package com.gridpointcode

import android.content.Context
import com.gridpointcode.core.NameMarks
import com.gridpointcode.core.NameSource
import com.gridpointcode.core.NameTable
import com.gridpointcode.core.Named
import com.gridpointcode.core.findNamedAcross
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Place names kept for searching with no network: the name index a country at
 * a time, as the site's builder writes it and the Landmarks workflow publishes
 * it, beside the repository as the `place-packs` release (BuildConfig.PACKS,
 * which a build can point elsewhere with -Pgpc.packs).
 *
 * A pack is the index's own lines for one country, in the index's order, so a
 * search of the kept packs merges into the order the site's file has and reads
 * them with the same code. Each is kept whole or not at all: its lines and bytes
 * are checked against what the list says before it counts as kept, and a pack
 * cut short is thrown away. Kept in the app's no-backup storage, as the kept
 * landmark areas are, since it can always be fetched again.
 */
class PlacePacks(context: Context, private val base: String = BuildConfig.PACKS) {

    /** A country's pack as the list offers it. */
    data class Offered(val code: String, val name: String, val download: Long, val lines: Int, val bytes: Long)

    /** A pack kept on the phone. */
    data class Kept(val code: String, val name: String, val bytes: Long, val built: String)

    private val dir = File(context.noBackupFilesDir, "place-packs")
    private val lock = Mutex()
    private var manifest: JSONObject? = null
    private val sources = mutableMapOf<String, NameSource>()

    /** Every country's pack, by name, or null with no connection to the list. */
    suspend fun offered(): List<Offered>? = withContext(Dispatchers.IO) {
        val list = runCatching { JSONObject(fetch("$base/packs.json").use { it.readBytes() }.toString(Charsets.UTF_8)) }.getOrNull()
            ?: return@withContext null
        manifest = list
        val countries = list.getJSONObject("countries")
        countries.keys().asSequence().map { code ->
            val entry = countries.getJSONObject(code)
            Offered(code, entry.getString("name"), entry.getLong("gzip"), entry.getInt("lines"), entry.getLong("bytes"))
        }.sortedBy { it.name }.toList()
    }

    /** Keeps [pack], whole, or nothing: false when it could not be fetched in full. */
    suspend fun keep(pack: Offered): Boolean = withContext(Dispatchers.IO) {
        lock.withLock {
            val list = manifest ?: return@withLock false
            dir.mkdirs()
            val part = File(dir, "${pack.code}.txt.part")
            val marks = NameMarks()
            val fetched = runCatching {
                GZIPInputStream(fetch("$base/${pack.code}.txt.gz")).bufferedReader(Charsets.UTF_8).useLines { lines ->
                    part.bufferedWriter(Charsets.UTF_8).use { out ->
                        lines.forEach { line ->
                            out.write(line)
                            out.write("\n")
                            marks.add(line)
                        }
                    }
                }
            }.isSuccess
            // What the list says the pack holds, to the line and the byte.
            if (!fetched || marks.lines != pack.lines || marks.bytes != pack.bytes) {
                part.delete()
                return@withLock false
            }
            val table = marks.table(list.getJSONArray("regions").let { regions -> List(regions.length(), regions::getString) })
            val about = JSONObject()
                .put("name", pack.name)
                .put("built", list.optString("built"))
                .put("bytes", table.bytes)
                .put("regions", JSONArray(table.regions))
                .put("keys", JSONArray(table.keys))
                .put("starts", JSONArray(table.starts.toList()))
            File(dir, "${pack.code}.json.part").writeText(about.toString())
            val text = File(dir, "${pack.code}.txt")
            val json = File(dir, "${pack.code}.json")
            part.renameTo(text) && File(dir, "${pack.code}.json.part").renameTo(json)
            sources.remove(pack.code)
            text.exists() && json.exists()
        }
    }

    /** The packs kept, by name. */
    fun kept(): List<Kept> =
        (dir.listFiles { file -> file.name.endsWith(".json") && !file.name.endsWith(".part") } ?: emptyArray())
            .mapNotNull { file ->
                runCatching {
                    val about = JSONObject(file.readText())
                    Kept(file.nameWithoutExtension, about.getString("name"), about.getLong("bytes"), about.optString("built"))
                }.getOrNull()
            }
            .sortedBy { it.name }

    fun forget(code: String) {
        sources.remove(code)
        File(dir, "$code.txt").delete()
        File(dir, "$code.json").delete()
    }

    /**
     * Places whose name begins with [query] in the kept packs, in the index's
     * order. Each pack's table is read once and kept, so a keystroke reads only
     * the block it lands in.
     */
    suspend fun find(query: String, most: Int = 12): List<Named> = withContext(Dispatchers.IO) {
        val codes = (dir.listFiles { file -> file.name.endsWith(".txt") } ?: emptyArray()).map { it.nameWithoutExtension }
        findNamedAcross(query, codes.mapNotNull(::source), most)
    }

    private fun source(code: String): NameSource? = sources[code] ?: runCatching {
        val about = JSONObject(File(dir, "$code.json").readText())
        fun strings(name: String) = about.getJSONArray(name).let { array -> List(array.length(), array::getString) }
        val starts = about.getJSONArray("starts").let { array -> LongArray(array.length(), array::getLong) }
        val table = NameTable(about.getLong("bytes"), strings("regions"), strings("keys"), starts)
        val text = File(dir, "$code.txt")
        NameSource(table) { from, until ->
            runCatching {
                RandomAccessFile(text, "r").use { file ->
                    val bytes = ByteArray((until - from).toInt())
                    file.seek(from)
                    file.readFully(bytes)
                    bytes.toString(Charsets.UTF_8)
                }
            }.getOrNull()
        }
    }.getOrNull()?.also { sources[code] = it }

    private fun fetch(address: String) = (URL(address).openConnection() as HttpURLConnection).run {
        connectTimeout = TIMEOUT_MS
        readTimeout = TIMEOUT_MS
        // A release asset redirects to where GitHub stores it, on another host.
        instanceFollowRedirects = true
        if (responseCode != HttpURLConnection.HTTP_OK) error("HTTP $responseCode for $address")
        inputStream
    }

    private companion object {
        const val TIMEOUT_MS = 15_000
    }
}
