package com.gridpointcode.wear

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.gridpointcode.core.FROM_WATCH_PATH
import com.gridpointcode.core.SAVED_PATH
import com.gridpointcode.core.SavedPlace
import com.gridpointcode.core.decodeSaved
import com.gridpointcode.core.decodeSavedList
import com.gridpointcode.core.encodeSaved
import com.gridpointcode.core.merging
import com.gridpointcode.core.savedAt
import com.gridpointcode.core.saving
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The saved places on the watch: the phone's list, as it last arrived, kept in
 * a file so the watch has them with the phone out of reach.
 *
 * The phone writes its list to the Data Layer whenever it changes; the watch
 * reads it when it opens, hears changes while it is open, and the listener
 * service keeps the file current while it is closed. A place saved on the watch
 * goes into the list here at once and to the phone as its own Data Layer item,
 * which the Data Layer holds until the phone is in reach.
 *
 * Until the phone has taken a place in, the phone's list does not have it, so
 * each list that arrives has the places still on their way laid over it. The
 * phone deletes a place's item once it has taken it in, and the watch deletes
 * its own when the phone's list shows it there, so an item left is one the
 * phone has not seen.
 */
class WatchShelf(context: Context) {

    private val app = context.applicationContext
    private val file = File(app.filesDir, CACHE)
    private val data: DataClient = Wearable.getDataClient(app)
    private val list = MutableStateFlow(read(file))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val saved: StateFlow<List<SavedPlace>> = list

    /** The phone's list as the Data Layer holds it now, if the phone has written one. */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        runCatching {
            val items = Tasks.await(data.getDataItems(Uri.parse("wear://*$SAVED_PATH")))
            val text = try {
                items.firstOrNull()?.let(::textOf)
            } finally {
                items.release()
            }
            text?.let(::take)
        }
    }

    /** What the listener hears while the app is open. */
    val listener = DataClient.OnDataChangedListener { events ->
        listFrom(events)?.let { text -> scope.launch { take(text) } }
    }

    fun listen() = data.addListener(listener)

    fun stop() = data.removeListener(listener)

    /** Saves a place here, and sends it to the phone. */
    suspend fun save(place: SavedPlace) = withContext(Dispatchers.IO) {
        // Held until the item is in the Data Layer, so a list arriving in
        // between cannot miss the place: it would be in neither.
        synchronized(LOCK) {
            val next = list.value.saving(place)
            list.value = next
            file.writeText(encodeSaved(next))
            runCatching {
                val request = PutDataRequest.create("$FROM_WATCH_PATH/${place.code}")
                    .setData(encodeSaved(listOf(place)).toByteArray())
                    .setUrgent()
                Tasks.await(data.putDataItem(request))
            }
        }
    }

    /**
     * Takes in the phone's list, with the places still on their way laid over
     * it. A list that does not read is not taken. Waits on the Data Layer, so
     * never on the main thread.
     */
    fun take(text: String) {
        val phone = decodeSavedList(text) ?: return
        synchronized(LOCK) {
            val waiting = waiting()
            val arrived = waiting.filter { (place, _) -> (phone.savedAt(place.code)?.savedAt ?: -1) >= place.savedAt }
            arrived.forEach { (_, uri) -> runCatching { Tasks.await(data.deleteDataItems(uri)) } }
            val next = phone.merging((waiting - arrived.toSet()).map { it.first })
            list.value = next
            file.writeText(encodeSaved(next))
        }
    }

    /** The places saved here whose items are still in the Data Layer, each with its item. */
    private fun waiting(): List<Pair<SavedPlace, Uri>> = runCatching {
        val items = Tasks.await(data.getDataItems(Uri.parse("wear://*$FROM_WATCH_PATH/"), DataClient.FILTER_PREFIX))
        try {
            items.flatMap { item -> decodeSaved(textOf(item).orEmpty()).map { it to item.uri } }
        } finally {
            items.release()
        }
    }.getOrDefault(emptyList())

    companion object {
        const val CACHE = "saved-places.txt"

        /** One writer at a time, the app and the listener service alike. */
        private val LOCK = Any()

        /** The list kept in [file], or none. */
        fun read(file: File): List<SavedPlace> = runCatching { decodeSaved(file.readText()) }.getOrDefault(emptyList())

        /**
         * The newest list among [events], read out now: the buffer is gone once
         * the callback returns.
         */
        fun listFrom(events: DataEventBuffer): String? =
            events.lastOrNull { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == SAVED_PATH }?.dataItem?.let(::textOf)

        private fun textOf(item: DataItem): String? = item.data?.let { String(it) }
    }
}
