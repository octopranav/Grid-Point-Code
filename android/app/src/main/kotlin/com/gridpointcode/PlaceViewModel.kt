package com.gridpointcode

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gridpointcode.core.Compass
import com.gridpointcode.core.Locating
import com.gridpointcode.core.Named
import com.gridpointcode.core.PlaceState
import com.gridpointcode.core.PlaceView
import com.gridpointcode.core.Point
import com.gridpointcode.core.Selection
import com.gridpointcode.core.Source
import com.gridpointcode.core.described
import com.gridpointcode.core.found
import com.gridpointcode.core.isName
import com.gridpointcode.core.located
import com.gridpointcode.core.locating
import com.gridpointcode.core.locationOff
import com.gridpointcode.core.locationRefused
import com.gridpointcode.core.nudged
import com.gridpointcode.core.opened
import com.gridpointcode.core.placed
import com.gridpointcode.core.selectionAt
import com.gridpointcode.core.stoppedLocating
import com.gridpointcode.core.view
import com.gridpointcode.map.Basemap
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Holds the place, and listens to the device when asked.
 *
 * What a fix may do to the place, and what survives a move, are decided in
 * `:core` where they are tested. This class keeps the state across a rotation,
 * hands the screen a view derived from it in one pass, and makes sure the
 * satellites are not left running once nobody is waiting for them.
 */
class PlaceViewModel(application: Application) : AndroidViewModel(application) {

    private val device = DeviceLocation(application)
    private val preferences = Preferences(application)
    private val chosen = MutableStateFlow(preferences.basemap())
    private val state = MutableStateFlow(PlaceState(selectionAt(SAMPLE, Source.SAMPLE)))
    private var listening: Job? = null
    private val names = NameIndex()
    private val finding = MutableStateFlow(Finding())
    private var looking: Job? = null

    val ui: StateFlow<PlaceView> = state
        .map { it.view() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, state.value.view())

    /** The basemap the reader chose, remembered across launches. */
    val basemap: StateFlow<Basemap> = chosen

    fun choose(next: Basemap) {
        chosen.value = next
        preferences.remember(next)
    }

    /**
     * What is in the search field. Kept here rather than in the field, so that
     * choosing a place can empty it, however the place was chosen.
     */
    var query by mutableStateOf("")
        private set

    /** What the search field has turned up by name. */
    val found: StateFlow<Finding> = finding

    /** A place from the map. */
    fun place(next: Selection) {
        settle()
        change { it.placed(next) }
    }

    fun nudge(direction: Compass) = change { it.nudged(direction) }

    fun open(text: String, source: Source = Source.CODE) {
        settle()
        change { it.opened(text, source) }
    }

    /**
     * The search field changed. It is looked up by name only when it is nothing
     * else, and only once the typing pauses: a keystroke is not worth a request.
     * While a search is out, the places already listed stay listed, so the list
     * does not blink with every letter.
     */
    fun type(text: String) {
        query = text
        looking?.cancel()
        if (!isName(text)) {
            finding.value = Finding()
            return
        }
        looking = viewModelScope.launch {
            delay(TYPING_MS)
            finding.update { it.copy(query = text, status = Finding.Status.LOOKING) }
            finding.value = answer(text, names.find(text))
        }
    }

    /** A place chosen from the list. */
    fun pick(place: Named) {
        settle()
        query = ""
        change { it.found(place) }
    }

    /**
     * Go. A code, a point or a link opens as it always has. A name goes to the
     * first place listed for it, which for a whole name is the largest place
     * called that.
     */
    fun go(text: String) {
        if (!isName(text)) return open(text)
        looking?.cancel()
        looking = viewModelScope.launch {
            val places = names.find(text)
            val first = places?.firstOrNull()
            if (first == null) {
                finding.value = answer(text, places)
            } else {
                finding.value = Finding()
                query = ""
                change { it.found(first) }
            }
        }
    }

    fun describeTheWay(text: String) = state.update { it.described(text) }

    /** The reader said no to the permission prompt. */
    fun refused() = change { it.locationRefused() }

    /**
     * Listen until a fix is inside one cell, the reader goes somewhere else, or
     * patience runs out. The first fix arrives in a second or two with a warm
     * start, and can take much longer indoors; the screen says which is happening.
     */
    fun locate() {
        if (!device.permitted()) return change { it.locationRefused() }
        if (!device.enabled()) return change { it.locationOff() }

        listening?.cancel()
        state.update { it.locating() }
        listening = viewModelScope.launch {
            withTimeoutOrNull(PATIENCE_MS) {
                device.fixes().firstOrNull { fix ->
                    state.update { it.located(Point(fix.latitude, fix.longitude), fix.accuracy.toDouble()) }
                    state.value.locating == Locating.IDLE
                }
            }
            if (state.value.locating != Locating.IDLE) state.update { it.stoppedLocating() }
        }
    }

    /** A place has been chosen some other way, so nothing still being looked up may arrive after it. */
    private fun settle() {
        looking?.cancel()
        looking = null
        finding.value = Finding()
    }

    private fun answer(query: String, places: List<Named>?): Finding = when {
        places == null -> Finding(query, status = Finding.Status.OFFLINE)
        places.isEmpty() -> Finding(query, status = Finding.Status.MISSING)
        else -> Finding(query, places, Finding.Status.FOUND)
    }

    /** Every change that can end listening also stops the device, so nothing runs for nobody. */
    private fun change(transition: (PlaceState) -> PlaceState) {
        state.update(transition)
        if (state.value.locating == Locating.IDLE) {
            listening?.cancel()
            listening = null
        }
    }

    private companion object {
        /** The specification's own example, until the device says where it is. */
        val SAMPLE = Point(43.650006, -79.380004)

        /** Long enough for a cold start by a window; a fix inside one cell ends it sooner. */
        const val PATIENCE_MS = 30_000L

        /** The pause in typing that counts as having typed something, the same as the website's. */
        const val TYPING_MS = 180L
    }
}

/** What the search field has turned up by name. */
data class Finding(
    val query: String = "",
    val places: List<Named> = emptyList(),
    val status: Status = Status.IDLE,
) {
    enum class Status {
        IDLE,
        LOOKING,
        FOUND,

        /** Nothing in the index is called that. */
        MISSING,

        /** The index could not be reached, which is not the same as nothing found. */
        OFFLINE,
    }
}
