package com.gridpointcode.wear

import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService

/**
 * Keeps the watch's copy of the saved places current while the app is closed:
 * the phone's list, taken in as the open app takes it and written to the file
 * the app reads when it opens. This runs off the main thread, so it may wait
 * on the Data Layer.
 */
class SavedListener : WearableListenerService() {

    override fun onDataChanged(events: DataEventBuffer) {
        WatchShelf.listFrom(events)?.let { WatchShelf(this).take(it) }
    }
}
