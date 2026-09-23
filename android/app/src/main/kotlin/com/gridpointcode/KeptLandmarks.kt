package com.gridpointcode

import java.io.File
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

/**
 * The areas of the landmark archive a reader asked to keep, on the device.
 *
 * Only what was asked for. Shards fetched along the way while looking around
 * are held in memory and forgotten when the app closes, so telling a reader an
 * area is kept always means they kept it: an area merely glanced at is not
 * ready for a journey, and saying so would read as reassurance exactly when it
 * should not.
 *
 * Laid out as the site serves it: each shard's own file, byte for byte, beside
 * the manifest of the archive they came from and a list of the areas kept. The
 * app keeps the folder out of the device's backup, because all of it can be
 * fetched again, and out of the cache, which the system may empty when space
 * runs short. Everything here blocks, and is called off the main thread.
 */
class KeptLandmarks(private val dir: File) {

    /** The archive the kept shards were cut from. */
    data class Source(val level: Int, val built: String)

    /** One area kept: how many of its shards hold anything, and their size. */
    data class Area(val cell: String, val shards: Int, val bytes: Long, val keptOn: LocalDate, val built: String)

    private val shards = File(dir, "shards")
    private val manifest = File(dir, "manifest.json")
    private val index = File(dir, "areas.json")

    fun source(): Source? = read(manifest)?.let { json ->
        Source(json.getInt("level"), json.getString("built"))
    }

    /** A kept shard's file, or null when it is not kept. An ocean shard is kept as empty. */
    fun shard(name: String): String? = File(shards, "$name.json").takeIf { it.isFile }?.readText()

    fun areas(): List<Area> {
        val list = runCatching { JSONArray(index.readText()) }.getOrNull() ?: return emptyList()
        return List(list.length()) { i ->
            val area = list.getJSONObject(i)
            Area(
                cell = area.getString("cell"),
                shards = area.getInt("shards"),
                bytes = area.getLong("bytes"),
                keptOn = LocalDate.parse(area.getString("keptOn")),
                built = area.getString("built"),
            )
        }
    }

    /** Every kept shard's file, for looking a landmark up by name with no connection. */
    fun everyShard(): List<String> = shards.listFiles().orEmpty().filter { it.isFile }.map { it.readText() }

    /**
     * Keep an area: every one of its shards, by name, with null for a shard that
     * does not exist. Written only once all of them are in hand, so an area is
     * never recorded as kept with part of it missing.
     */
    fun keep(source: Source, cell: String, bodies: Map<String, String?>): Area {
        shards.mkdirs()
        var held = 0
        var bytes = 0L
        for ((name, body) in bodies) {
            File(shards, "$name.json").writeText(body ?: OCEAN)
            if (body != null) {
                held += 1
                bytes += body.toByteArray().size
            }
        }
        manifest.writeText(JSONObject().put("level", source.level).put("built", source.built).toString())
        val area = Area(cell, held, bytes, LocalDate.now(), source.built)
        val others = areas().filter { it.cell != cell }
        index.writeText(JSONArray((others + area).map { it.json() }).toString())
        return area
    }

    /** Give it all back. */
    fun forget() {
        dir.deleteRecursively()
    }

    private fun Area.json() = JSONObject()
        .put("cell", cell)
        .put("shards", shards)
        .put("bytes", bytes)
        .put("keptOn", keptOn.toString())
        .put("built", built)

    private fun read(file: File): JSONObject? = runCatching { JSONObject(file.readText()) }.getOrNull()

    private companion object {
        /** A shard with nothing in it, which is most of the planet. */
        const val OCEAN = """{"regions":[],"landmarks":[]}"""
    }
}
