package com.gridpointcode.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ca.pranavpatel.algo.gridpointcode.design.LocalGpcColors
import ca.pranavpatel.algo.gridpointcode.design.Radius
import ca.pranavpatel.algo.gridpointcode.design.Space
import com.gridpointcode.core.Box as CellEdges
import com.gridpointcode.core.Point
import com.gridpointcode.core.Selection
import com.gridpointcode.core.Source
import com.gridpointcode.core.cellBox
import com.gridpointcode.core.neighbourBoxes
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineBlur
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Polygon
import kotlin.math.max
import org.maplibre.geojson.Point as GeoPoint

/**
 * The basemaps the tile provider offers, the same list the website gives.
 *
 * Positron and fiord are the desaturated pair, which is what a drawing laid on
 * top needs: the cell has to be the brightest thing on the screen. They are what
 * [AUTO] picks, following the light or dark theme.
 */
enum class Basemap(internal val style: String?) {
    AUTO(null),
    POSITRON("positron"),
    BRIGHT("bright"),
    LIBERTY("liberty"),
    DARK("dark"),
    FIORD("fiord"),
}

internal fun styleFor(basemap: Basemap, dark: Boolean): String =
    BuildConfig.STYLES + (basemap.style ?: if (dark) "fiord" else "positron")

/** Close enough to see the cell as a shape, and the street it is on. */
private const val START_ZOOM = 19.0

/** A place arriving from elsewhere is shown at least this close. */
private const val FOLLOW_ZOOM = 17.0

/**
 * How long a map may take before the screen says it has not arrived.
 *
 * With no connection the renderer never reports a failure: it waits, quietly,
 * for the network to come back. A notice that waited for the failure would
 * never appear, and an empty rectangle would be left to be read as a fault.
 */
private const val PATIENCE_MS = 6_000L

private const val CELL = "gpc-cell"
private const val AROUND = "gpc-around"

/**
 * The map under the place: the cell in brass, the eight around it faint, and a
 * tap anywhere to put the point there.
 *
 * It is an illustration of an answer the screen already has. Everything the
 * place panel says is arithmetic on the device, so nothing waits on this; if the
 * tiles never arrive, the panel is complete and a line here says the map is
 * missing rather than leaving a grey rectangle to be read as a fault.
 *
 * North stays up. The grid runs along latitude and longitude, and a rotated map
 * turns the cell into a diamond that no longer looks like what it names.
 *
 * [padding] is the part of the map something else covers, a search bar above
 * or a sheet below, so the cell is centred in what can actually be seen.
 */
@Composable
fun PlaceMap(
    selection: Selection,
    onPick: (Point) -> Unit,
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(0.dp),
    basemap: Basemap = Basemap.AUTO,
    dark: Boolean = isSystemInDarkTheme(),
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val inset = with(density) {
        listOf(
            padding.calculateLeftPadding(direction),
            padding.calculateTopPadding(),
            padding.calculateRightPadding(direction),
            padding.calculateBottomPadding(),
        ).map { it.toPx().toDouble() }
    }
    val colours = LocalGpcColors.current
    val ground = MaterialTheme.colorScheme.background.toArgb()
    val pick by rememberUpdatedState(onPick)

    var failed by remember { mutableStateOf(false) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var placed by remember { mutableStateOf(false) }

    val view = remember {
        MapLibre.getInstance(context)
        // Until a style arrives the map is painted in the page's own ground,
        // so a map still loading reads as quiet rather than broken.
        val options = MapLibreMapOptions.createFromAttributes(context).foregroundLoadColor(ground)
        MapView(context, options).apply {
            onCreate(null)
            addOnDidFailLoadingMapListener { failed = true }
            addOnDidFinishLoadingStyleListener { failed = false }
            getMapAsync { ready ->
                ready.uiSettings.setRotateGesturesEnabled(false)
                ready.uiSettings.setTiltGesturesEnabled(false)
                ready.addOnMapClickListener { at ->
                    pick(Point(at.latitude, at.longitude))
                    true
                }
                map = ready
            }
        }
    }

    // The map view has its own lifecycle, and nothing forwards it for us.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, view) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> view.onStart()
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_STOP -> view.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            view.onDestroy()
        }
    }

    // A new style throws away every layer added to the old one, so the drawing
    // is added again each time; the colours change with the theme anyway.
    val url = styleFor(basemap, dark)
    val brass = colours.code.toArgb()
    val soft = colours.inkSoft.toArgb()
    LaunchedEffect(map, url, brass, soft) {
        val ready = map ?: return@LaunchedEffect
        style = null
        ready.setStyle(Style.Builder().fromUri(url)) { loaded ->
            addDrawing(loaded, brass, soft)
            style = loaded
        }
    }

    // Said after a while without a style, whatever the reason, and withdrawn the
    // moment one loads: the renderer retries by itself when the network returns.
    LaunchedEffect(map, url, style) {
        if (style != null) {
            failed = false
            return@LaunchedEffect
        }
        delay(PATIENCE_MS)
        if (style == null) failed = true
    }

    LaunchedEffect(style, selection.code) {
        style?.let { draw(it, selection.code) }
    }

    LaunchedEffect(map, selection) {
        val ready = map ?: return@LaunchedEffect
        follow(ready, selection, inset.toDoubleArray(), first = !placed)
        placed = true
    }

    LaunchedEffect(map, inset) {
        map?.moveCamera(CameraUpdateFactory.paddingTo(inset.toDoubleArray()))
    }

    Box(modifier) {
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
        if (failed) {
            // Set in ink on purpose: brass is the colour of a code, and this is not one.
            Text(
                text = stringResource(R.string.map_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(Space.step4)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                    .border(1.dp, colours.rule, RoundedCornerShape(Radius.card))
                    .padding(Space.step3),
            )
        }
    }
}

private fun addDrawing(style: Style, brass: Int, soft: Int) {
    style.addSource(GeoJsonSource(AROUND))
    style.addSource(GeoJsonSource(CELL))
    style.addLayer(FillLayer("$AROUND-fill", AROUND).withProperties(fillColor(soft), fillOpacity(0.05f)))
    style.addLayer(LineLayer("$AROUND-line", AROUND).withProperties(lineColor(soft), lineWidth(0.8f), lineOpacity(0.6f)))
    style.addLayer(LineLayer("$CELL-glow", CELL).withProperties(lineColor(brass), lineWidth(9f), lineBlur(7f), lineOpacity(0.55f)))
    style.addLayer(LineLayer("$CELL-line", CELL).withProperties(lineColor(brass), lineWidth(2f)))
}

private fun draw(style: Style, code: String) {
    style.getSourceAs<GeoJsonSource>(CELL)?.setGeoJson(outline(cellBox(code)))
    style.getSourceAs<GeoJsonSource>(AROUND)
        ?.setGeoJson(FeatureCollection.fromFeatures(neighbourBoxes(code).map(::outline)))
}

/** A cell as a closed ring, longitude first as GeoJSON wants it. */
private fun outline(box: CellEdges): Feature = Feature.fromGeometry(
    Polygon.fromLngLats(
        listOf(
            listOf(
                GeoPoint.fromLngLat(box.west, box.south),
                GeoPoint.fromLngLat(box.east, box.south),
                GeoPoint.fromLngLat(box.east, box.north),
                GeoPoint.fromLngLat(box.west, box.north),
                GeoPoint.fromLngLat(box.west, box.south),
            ),
        ),
    ),
)

/**
 * Keep the place in view without taking the map away from the reader.
 *
 * A tap or a nudge happens where the reader is already looking, so the camera
 * stays put unless the place has left the screen. Anything arriving from
 * elsewhere, a link, a typed code, the device, is flown to.
 */
private fun follow(map: MapLibreMap, selection: Selection, inset: DoubleArray, first: Boolean) {
    val target = LatLng(selection.point.latitude, selection.point.longitude)
    if (first) {
        map.cameraPosition = CameraPosition.Builder().target(target).zoom(START_ZOOM).padding(inset).build()
        return
    }
    // Counted against the uncovered part only: a place under the sheet is not in view.
    val local = selection.source == Source.MAP || selection.source == Source.NUDGE
    if (local && map.projection.getVisibleRegion(false).latLngBounds.contains(target)) return
    val zoom = max(map.cameraPosition.zoom, FOLLOW_ZOOM)
    map.easeCamera(
        CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(target).zoom(zoom).padding(inset).build()),
        700,
    )
}
