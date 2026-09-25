package com.gridpointcode.wear

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gridpointcode.core.Point
import com.gridpointcode.core.SavedPlace
import com.gridpointcode.core.Source
import com.gridpointcode.core.selectionAt
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Where the watch is, and how far to trust it, in whole metres. */
data class WatchFix(val point: Point, val metres: Int) {
    val code: String get() = selectionAt(point, Source.DEVICE).code
}

/**
 * The watch's state: where it is, which way it faces, and the saved places.
 * The location and the compass run only while a screen is showing, and stop
 * the moment it is not; a watch battery is small.
 */
class WatchModel(application: Application) : AndroidViewModel(application) {

    val shelf = WatchShelf(application)
    private val location = WatchLocation(application)
    private val compass = WatchCompass(application)
    private val found = MutableStateFlow<WatchFix?>(null)
    private val facing = MutableStateFlow<Float?>(null)
    private val allowed = MutableStateFlow(location.permitted())
    private var listening: Job? = null
    private var turning: Job? = null

    val fix: StateFlow<WatchFix?> = found

    /** Degrees clockwise from north the watch faces, or null with no compass. */
    val heading: StateFlow<Float?> = facing

    val permitted: StateFlow<Boolean> = allowed

    init {
        viewModelScope.launch { shelf.refresh() }
    }

    fun start() {
        allowed.value = location.permitted()
        if (listening == null && allowed.value) {
            listening = viewModelScope.launch {
                location.fixes().collect { found.value = WatchFix(Point(it.latitude, it.longitude), it.accuracy.roundToInt()) }
            }
        }
        if (turning == null && compass.available) {
            turning = viewModelScope.launch { compass.headings().collect { facing.value = it } }
        }
        shelf.listen()
    }

    fun stop() {
        listening?.cancel()
        listening = null
        turning?.cancel()
        turning = null
        shelf.stop()
    }

    /** Saves where the watch is, with no name: the phone's keyboard is the place to give it one. */
    fun saveHere() {
        val here = found.value ?: return
        viewModelScope.launch { shelf.save(SavedPlace(here.code, label = "", note = "", savedAt = System.currentTimeMillis())) }
    }
}
