package com.gridpointcode

import android.content.Context
import com.gridpointcode.map.Basemap

/**
 * What the app remembers between launches: how the reader likes to look at
 * things, and nothing about where they have been.
 *
 * Kept in the platform's own preferences file. One small value does not need a
 * library, and it is read before the first frame, so the map never flashes one
 * style and then another.
 */
class Preferences(context: Context) {

    private val store = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)

    /** The reader's basemap, or following the theme if they never chose one. */
    fun basemap(): Basemap =
        store.getString(BASEMAP, null)
            ?.let { name -> Basemap.entries.firstOrNull { it.name == name } }
            ?: Basemap.AUTO

    fun remember(basemap: Basemap) {
        store.edit().putString(BASEMAP, basemap.name).apply()
    }

    private companion object {
        const val BASEMAP = "basemap"
    }
}
