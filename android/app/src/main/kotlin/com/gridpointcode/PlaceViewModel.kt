package com.gridpointcode

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gridpointcode.core.Compass
import com.gridpointcode.core.Locating
import com.gridpointcode.core.PlaceState
import com.gridpointcode.core.PlaceView
import com.gridpointcode.core.Point
import com.gridpointcode.core.Selection
import com.gridpointcode.core.Source
import com.gridpointcode.core.described
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
import kotlinx.coroutines.Job
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
    private val state = MutableStateFlow(PlaceState(selectionAt(SAMPLE, Source.SAMPLE)))
    private var listening: Job? = null

    val ui: StateFlow<PlaceView> = state
        .map { it.view() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, state.value.view())

    /** A place from the map. */
    fun place(next: Selection) = change { it.placed(next) }

    fun nudge(direction: Compass) = change { it.nudged(direction) }

    fun open(text: String, source: Source = Source.CODE) = change { it.opened(text, source) }

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
    }
}
