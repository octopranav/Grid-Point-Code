package com.gridpointcode

import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.gridpointcode.core.FROM_WATCH_PATH
import com.gridpointcode.core.WATCH_CAPABILITY
import com.gridpointcode.core.decodeSaved
import com.gridpointcode.core.merging
import kotlinx.coroutines.runBlocking

/**
 * Takes in a place saved on the watch, and sends the list to a watch that
 * newly has the app.
 *
 * The watch leaves each place as its own Data Layer item, which arrives here
 * whether the app is open or not. It is merged into the saved list, where a
 * later saving of the same code wins, and the item is then deleted, so it is
 * taken in once. These calls come off the main thread, so each waits for its
 * writing to finish before the service may be let go.
 */
class WatchListener : WearableListenerService() {

    override fun onDataChanged(events: DataEventBuffer) {
        SavedShelf.open(this)
        val data = Wearable.getDataClient(this)
        events.filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path.orEmpty().startsWith(FROM_WATCH_PATH) }
            .forEach { event ->
                val item = event.dataItem
                val arrived = item.data?.let { decodeSaved(String(it)) }.orEmpty()
                if (arrived.isNotEmpty()) runBlocking { SavedShelf.change { it.merging(arrived) }.join() }
                runCatching { Tasks.await(data.deleteDataItems(item.uri)) }
            }
    }

    override fun onCapabilityChanged(capability: CapabilityInfo) {
        if (capability.name != WATCH_CAPABILITY) return
        SavedShelf.open(this)
        runBlocking { SavedShelf.resend().join() }
    }
}
