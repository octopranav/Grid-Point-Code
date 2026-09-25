package com.gridpointcode

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.gridpointcode.core.SAVED_PATH
import com.gridpointcode.core.SavedPlace
import com.gridpointcode.core.WATCH_CAPABILITY
import com.gridpointcode.core.encodeSaved
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

/**
 * The saved places, one list for the whole app.
 *
 * The screen changes it, and so does the listener that takes in places saved
 * on the watch; both change it here, so neither can write over the other. Each
 * change is written to the file in the order it was made, and the whole list
 * goes to the Wear Data Layer for the watch.
 *
 * Only when a watch has the app: the list is handed to the Data Layer only
 * when one says so, and taken back out when none does, so a phone with no
 * watch keeps its places in its own file and nowhere else. Without Google's
 * services on the phone the Data Layer is not there at all, and the list is
 * simply not sent; nothing else here needs them.
 */
object SavedShelf {

    private val list = MutableStateFlow<List<SavedPlace>>(emptyList())
    private var store: SavedPlaces? = null
    private var context: Context? = null

    /** One at a time, in order, so a quick save and remove cannot land backwards. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val writing = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    val saved: StateFlow<List<SavedPlace>> = list

    /** Reads the file the first time the app or its listener needs the list, and sends it to the watch once. */
    @Synchronized
    fun open(app: Context) {
        if (store != null) return
        context = app.applicationContext
        store = SavedPlaces(File(app.filesDir, "saved-places.json")).also { list.value = it.load() }
        val first = list.value
        writing.launch { publish(first) }
    }

    /** Changes the list; the job is its writing to the file and to the watch. */
    fun change(transform: (List<SavedPlace>) -> List<SavedPlace>): Job {
        val next = list.updateAndGet(transform)
        return writing.launch {
            store?.store(next)
            publish(next)
        }
    }

    /** Sends the list again, as when a watch with the app first appears. */
    fun resend(): Job = writing.launch { publish(list.value) }

    private fun publish(places: List<SavedPlace>) {
        val app = context ?: return
        runCatching {
            val data = Wearable.getDataClient(app)
            val watches = Tasks.await(Wearable.getCapabilityClient(app).getCapability(WATCH_CAPABILITY, CapabilityClient.FILTER_ALL))
            if (watches.nodes.isEmpty()) {
                val here = Tasks.await(Wearable.getNodeClient(app).localNode).id
                Tasks.await(data.deleteDataItems(Uri.Builder().scheme("wear").authority(here).path(SAVED_PATH).build()))
            } else {
                val request = PutDataRequest.create(SAVED_PATH).setData(encodeSaved(places).toByteArray()).setUrgent()
                Tasks.await(data.putDataItem(request))
            }
        }
    }
}
