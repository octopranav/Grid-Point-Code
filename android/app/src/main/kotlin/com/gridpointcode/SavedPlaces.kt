package com.gridpointcode

import com.gridpointcode.core.SavedPlace
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * The reader's saved places, in one small file on the device.
 *
 * In the app's own files, which the device's backup includes: these are the
 * reader's, and a new phone should have them. Written whole to a file beside it
 * and moved into place, so a crash halfway through a write leaves the last list
 * rather than half of one. Everything here blocks, and is called off the main
 * thread, except the one read when the app opens.
 */
class SavedPlaces(private val file: File) {

    fun load(): List<SavedPlace> = runCatching {
        val list = JSONArray(file.readText())
        List(list.length()) { i ->
            val place = list.getJSONObject(i)
            SavedPlace(
                code = place.getString("code"),
                label = place.optString("label"),
                note = place.optString("note"),
                savedAt = place.optLong("savedAt"),
            )
        }
    }.getOrDefault(emptyList())

    fun store(places: List<SavedPlace>) {
        val json = JSONArray(
            places.map { place ->
                JSONObject()
                    .put("code", place.code)
                    .put("label", place.label)
                    .put("note", place.note)
                    .put("savedAt", place.savedAt)
            },
        )
        val next = File(file.parentFile, file.name + ".next")
        next.writeText(json.toString())
        next.renameTo(file)
    }
}
