package com.gridpointcode

import java.io.File
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

/**
 * The areas of the landmark archive a reader asked to keep, on the device.
 *
 * Only what was asked for is here, and only this is ever reported as kept.
 * Shards met while looking around are cached apart, in [SeenLandmarks], where
 * the system may clear them: an area merely glanced at is not ready for a
 * journey, and saying it was would read as reassurance exactly when it should
 * not.
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

    /**
     * One area kept, named by the shard it was kept around.
     *
     * @property held how many of its shards hold anything; most of the planet is ocean
     */
    data class Area(
        val centre: String,
        val shards: List<String>,
        val held: Int,
        val bytes: Long,
        val keptOn: LocalDate,
        val built: String,
    )

    private val shards = File(dir, "shards")
    private val manifest = File(dir, "manifest.json")
    private val index = File(dir, "areas.json")

    fun source(): Source? = runCatching {
        val json = JSONObject(manifest.readText())
        Source(json.getInt("level"), json.getString("built"))
    }.getOrNull()

    /** A kept shard's file, or null when it is not kept. An ocean shard is kept as empty. */
    fun shard(name: String): String? = File(shards, "$name.json").takeIf { it.isFile }?.readText()

    fun has(name: String): Boolean = File(shards, "$name.json").isFile

    fun areas(): List<Area> {
        val list = runCatching { JSONArray(index.readText()) }.getOrNull() ?: return emptyList()
        return List(list.length()) { i ->
            val area = list.getJSONObject(i)
            val names = area.getJSONArray("shards")
            Area(
                centre = area.getString("centre"),
                shards = List(names.length()) { names.getString(it) },
                held = area.getInt("held"),
                bytes = area.getLong("bytes"),
                keptOn = LocalDate.parse(area.getString("keptOn")),
                built = area.getString("built"),
            )
        }
    }

    /** Every kept shard's file, for finding a place by name with no connection. */
    fun everyShard(): List<String> = shards.listFiles().orEmpty().filter { it.isFile }.map { it.readText() }

    /**
     * Keep an area: every one of its shards, by name, with null for a shard that
     * does not exist. Written only once all of them are in hand, so an area is
     * never recorded as kept with part of it missing.
     */
    fun keep(source: Source, centre: String, bodies: Map<String, String?>): Area {
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
        val area = Area(centre, bodies.keys.toList(), held, bytes, LocalDate.now(), source.built)
        val others = areas().filter { it.centre != centre }
        index.writeText(JSONArray((others + area).map { it.json() }).toString())
        return area
    }

    /** Give it all back. */
    fun forget() {
        dir.deleteRecursively()
    }

    private fun Area.json() = JSONObject()
        .put("centre", centre)
        .put("shards", JSONArray(shards))
        .put("held", held)
        .put("bytes", bytes)
        .put("keptOn", keptOn.toString())
        .put("built", built)

    companion object {
        /** A shard with nothing in it, which is most of the planet. */
        const val OCEAN = """{"regions":[],"landmarks":[]}"""
    }
}
