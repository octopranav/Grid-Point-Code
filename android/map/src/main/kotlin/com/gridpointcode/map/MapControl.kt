package com.gridpointcode.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap

/**
 * What a screen may ask of the map besides drawing a place: a step in or out,
 * for a reader with a mouse and no pinch. Handed to [PlaceMap], which connects
 * it once the map is ready; until then a step does nothing.
 */
class MapControl internal constructor() {
    internal var map: MapLibreMap? = null

    fun zoomIn() {
        map?.animateCamera(CameraUpdateFactory.zoomIn(), STEP_MS)
    }

    fun zoomOut() {
        map?.animateCamera(CameraUpdateFactory.zoomOut(), STEP_MS)
    }
}

/** A control for one map, kept across recompositions. */
@Composable
fun rememberMapControl(): MapControl = remember { MapControl() }

/** Long enough to see which way the map went, short enough to press again. */
private const val STEP_MS = 250
