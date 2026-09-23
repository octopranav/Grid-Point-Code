package com.gridpointcode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gridpointcode.core.Compass
import com.gridpointcode.core.PlaceState
import com.gridpointcode.core.PlaceView
import com.gridpointcode.core.Point
import com.gridpointcode.core.Selection
import com.gridpointcode.core.Source
import com.gridpointcode.core.described
import com.gridpointcode.core.nudged
import com.gridpointcode.core.opened
import com.gridpointcode.core.placed
import com.gridpointcode.core.selectionAt
import com.gridpointcode.core.view
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * Holds the place and nothing else.
 *
 * What survives a move, and what a piece of text turns out to be, are decided in
 * `:core` where they are tested. This class only keeps the current state across
 * a rotation and hands the screen a view derived from it in one pass.
 */
class PlaceViewModel : ViewModel() {

    private val state = MutableStateFlow(PlaceState(selectionAt(SAMPLE, Source.SAMPLE)))

    val ui: StateFlow<PlaceView> = state
        .map { it.view() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, state.value.view())

    /** A place from the device or the map, when those arrive. */
    fun place(next: Selection) = state.update { it.placed(next) }

    fun nudge(direction: Compass) = state.update { it.nudged(direction) }

    fun open(text: String, source: Source = Source.CODE) = state.update { it.opened(text, source) }

    fun describeTheWay(text: String) = state.update { it.described(text) }

    private companion object {
        /** The specification's own example, until the device says where it is. */
        val SAMPLE = Point(43.650006, -79.380004)
    }
}
