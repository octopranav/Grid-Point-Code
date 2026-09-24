package com.gridpointcode

import java.io.File
import org.json.JSONObject

/**
 * Shards of the landmark archive met while looking around, cached on the device
 * so the places already looked at still have their landmarks with no
 * connection, the way the map keeps the tiles already drawn.
 *
 * A convenience and nobody's decision, so never counted or reported as kept:
 * it lives in the app's cache, which the system may clear when space runs
 * short, and it is trimmed to a few megabytes, oldest first. Forget empties it
 * with the areas kept, since a reader pressing it wants the space back and does
 * not care which store held it.
 */
class SeenLandmarks(private val dir: File) {

    private val shards = File(dir, "shards")
    private val manifest = File(dir, "manifest.json")

    /** The archive last served, so the shards cut from it can be found by name offline. */
    fun source(): KeptLandmarks.Source? = runCatching {
        val json = JSONObject(manifest.readText())
        KeptLandmarks.Source(json.getInt("level"), json.getString("built"))
    }.getOrNull()

    fun remember(source: KeptLandmarks.Source) {
        dir.mkdirs()
        manifest.writeText(JSONObject().put("level", source.level).put("built", source.built).toString())
    }

    fun shard(name: String): String? = File(shards, "$name.json").takeIf { it.isFile }?.readText()

    /** A shard just fetched, or null for one that does not exist. */
    fun put(name: String, body: String?) {
        shards.mkdirs()
        File(shards, "$name.json").writeText(body ?: KeptLandmarks.OCEAN)
        trim()
    }

    fun everyShard(): List<String> = shards.listFiles().orEmpty().filter { it.isFile }.map { it.readText() }

    fun forget() {
        dir.deleteRecursively()
    }

    private fun trim() {
        val files = shards.listFiles().orEmpty().filter { it.isFile }.sortedBy { it.lastModified() }
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= MOST_BYTES) break
            total -= file.length()
            file.delete()
        }
    }

    private companion object {
        /** Hundreds of places' worth: most shards are a few kilobytes. */
        const val MOST_BYTES = 20L * 1024 * 1024
    }
}
