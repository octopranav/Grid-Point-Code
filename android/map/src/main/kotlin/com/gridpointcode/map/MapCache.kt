package com.gridpointcode.map

import android.content.Context
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import org.maplibre.android.offline.OfflineManager

/**
 * How much of what the map has drawn it keeps.
 *
 * The map library keeps every tile, style and font it fetches in a database on
 * the device, and draws from it when there is no connection, so a place already
 * looked at still has its map offline. By default it keeps 50 MB. This raises
 * that to 200 MB, a few areas of city streets at full detail, and the library
 * lets the oldest go first once it is full.
 *
 * Nothing is fetched ahead of being looked at. The tile provider's terms rule
 * out collecting its data in automated ways without permission, and downloading
 * an area in the background would be exactly that, so offline the map is what
 * the reader has seen.
 */
internal object MapCache {

    private const val BYTES = 200L * 1024 * 1024

    private val prepared = AtomicBoolean(false)

    fun prepare(context: Context) {
        if (!prepared.compareAndSet(false, true)) return
        OfflineManager.getInstance(context).setMaximumAmbientCacheSize(
            BYTES,
            object : OfflineManager.FileSourceCallback {
                override fun onSuccess() = Unit

                override fun onError(message: String) {
                    // The default size still works; the next launch tries again.
                    prepared.set(false)
                    Log.w("MapCache", message)
                }
            },
        )
    }
}
