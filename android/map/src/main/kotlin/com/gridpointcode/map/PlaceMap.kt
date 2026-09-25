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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
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
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
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
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.tan
import org.maplibre.geojson.Point as GeoPoint

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
private const val FIX = "gpc-fix"
private const val DOT = "gpc-dot"
private const val SAVED = "gpc-saved"
private const val AREA = "gpc-area"

/** Room around an area framed on screen, so its edges are seen as edges, in density-independent pixels. */
private const val AREA_MARGIN = 28

/** The world's width at zoom 0, in density-independent pixels. */
private const val WORLD_DP = 512.0

/** How often to look again for a map not yet laid out. */
private const val LAYOUT_POLL_MS = 50L

/** The mean radius of the earth, which is plenty for drawing a disc a few metres across. */
private const val EARTH_METRES = 6_371_008.8

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
 * or a sheet below, so the cell is centred in what can actually be seen, and the
 * map's credit sits in the corner that is left. [creditEnd] keeps that line
 * clear of whatever the screen puts in the other corner.
 */
@Composable
fun PlaceMap(
    selection: Selection,
    onPick: (Point) -> Unit,
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(0.dp),
    basemap: Basemap = Basemap.AUTO,
    dark: Boolean = isSystemInDarkTheme(),
    saved: List<Point> = emptyList(),
    area: CellEdges? = null,
    creditEnd: Dp = 0.dp,
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
    var credit by remember { mutableStateOf<String?>(null) }

    val view = remember {
        MapLibre.getInstance(context)
        MapCache.prepare(context)
        // Until a style arrives the map is painted in the page's own ground,
        // so a map still loading reads as quiet rather than broken.
        val options = MapLibreMapOptions.createFromAttributes(context).foregroundLoadColor(ground)
        MapView(context, options).apply {
            onCreate(null)
            addOnDidFailLoadingMapListener { failed = true }
            addOnDidFinishLoadingStyleListener { failed = false }
            // A source's credit arrives with its description of its tiles, a
            // moment after the style that names it.
            addOnSourceChangedListener { credit = style?.let(::declaredBy) }
            getMapAsync { ready ->
                ready.uiSettings.setRotateGesturesEnabled(false)
                ready.uiSettings.setTiltGesturesEnabled(false)
                // Drawn below the sheet, where nobody could see them; the
                // credit is drawn by this screen instead, where it can be.
                ready.uiSettings.setAttributionEnabled(false)
                ready.uiSettings.setLogoEnabled(false)
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
    // is added again each time, in the inks that suit the new map.
    val resolved = resolve(basemap, dark)
    val url = resolved.url
    val ink = inkFor(resolved.dark)
    LaunchedEffect(map, url, ink) {
        val ready = map ?: return@LaunchedEffect
        style = null
        ready.setStyle(Style.Builder().fromUri(url)) { loaded ->
            addDrawing(loaded, ink)
            style = loaded
            credit = declaredBy(loaded)
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

    // An area is drawn instead of the place: at the size of a district, a
    // doorway at its centre would only say there is a door there.
    LaunchedEffect(style, selection, area) {
        val loaded = style ?: return@LaunchedEffect
        if (area == null) draw(loaded, selection) else clearPlace(loaded)
        loaded.getSourceAs<GeoJsonSource>(AREA)?.setGeoJson(
            area?.let { FeatureCollection.fromFeature(outline(it)) } ?: FeatureCollection.fromFeatures(emptyList()),
        )
    }

    // Framed on the area when there is one, and back on the place underneath
    // when it goes. A place chosen instead, by a tap or a code, is left to the
    // selection's own camera.
    var framedOver by remember { mutableStateOf<Selection?>(null) }
    LaunchedEffect(map, area) {
        val ready = map ?: return@LaunchedEffect
        if (area != null) {
            // A link can open the app before the map has a size to fit anything to.
            while (view.width == 0 || view.height == 0) delay(LAYOUT_POLL_MS)
            ready.easeCamera(
                CameraUpdateFactory.newCameraPosition(framing(area, view, inset.toDoubleArray(), density.density)),
                700,
            )
            framedOver = selection
        } else if (framedOver != null) {
            val underneath = framedOver == selection
            framedOver = null
            if (underneath) ready.easeCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(selection.point.latitude, selection.point.longitude))
                        .zoom(START_ZOOM)
                        .padding(inset.toDoubleArray())
                        .build(),
                ),
                700,
            )
        }
    }

    LaunchedEffect(style, saved) {
        style?.getSourceAs<GeoJsonSource>(SAVED)?.setGeoJson(
            FeatureCollection.fromFeatures(saved.map { Feature.fromGeometry(GeoPoint.fromLngLat(it.longitude, it.latitude)) }),
        )
    }

    // An area's centre is not flown to: the area is framed whole instead.
    LaunchedEffect(map, selection) {
        val ready = map ?: return@LaunchedEffect
        if (selection.source != Source.AREA) follow(ready, selection, inset.toDoubleArray(), first = !placed)
        placed = true
    }

    LaunchedEffect(map, inset) {
        map?.moveCamera(CameraUpdateFactory.paddingTo(inset.toDoubleArray()))
    }

    Box(modifier) {
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
        if (style != null) {
            Credit(
                html = credit ?: PROVIDER_CREDIT,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(padding)
                    .padding(start = Space.step1, bottom = Space.step1, end = creditEnd + Space.step1),
            )
        }
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

/**
 * Bottom to top: the device's accuracy, the neighbours, the cell, and the dot.
 * The disc is under the cell on purpose. When it is wider than the cell, which
 * is most of the time, the reader should see both, and see which is bigger.
 */
private fun addDrawing(style: Style, ink: Ink) {
    // The reader's saved places lie under everything: context for the place on
    // screen, never mistaken for it.
    style.addSource(GeoJsonSource(SAVED))
    style.addLayer(
        CircleLayer("$SAVED-circle", SAVED).withProperties(
            circleColor(ink.halo),
            circleRadius(4.5f),
            circleStrokeColor(ink.prussian),
            circleStrokeWidth(2f),
        ),
    )
    style.addSource(GeoJsonSource(AREA))
    style.addLayer(FillLayer("$AREA-fill", AREA).withProperties(fillColor(ink.brass), fillOpacity(0.06f)))
    style.addLayer(LineLayer("$AREA-line", AREA).withProperties(lineColor(ink.brass), lineWidth(2f)))
    style.addSource(GeoJsonSource(FIX))
    style.addSource(GeoJsonSource(AROUND))
    style.addSource(GeoJsonSource(CELL))
    style.addSource(GeoJsonSource(DOT))
    style.addLayer(FillLayer("$FIX-fill", FIX).withProperties(fillColor(ink.prussian), fillOpacity(0.08f)))
    style.addLayer(LineLayer("$FIX-line", FIX).withProperties(lineColor(ink.prussian), lineWidth(1f), lineOpacity(0.35f)))
    style.addLayer(FillLayer("$AROUND-fill", AROUND).withProperties(fillColor(ink.soft), fillOpacity(0.05f)))
    style.addLayer(LineLayer("$AROUND-line", AROUND).withProperties(lineColor(ink.soft), lineWidth(0.8f), lineOpacity(0.6f)))
    style.addLayer(LineLayer("$CELL-glow", CELL).withProperties(lineColor(ink.brass), lineWidth(9f), lineBlur(7f), lineOpacity(0.55f)))
    style.addLayer(LineLayer("$CELL-line", CELL).withProperties(lineColor(ink.brass), lineWidth(2f)))
    style.addLayer(
        CircleLayer("$DOT-circle", DOT).withProperties(
            circleColor(ink.prussian),
            circleRadius(6.5f),
            circleStrokeColor(ink.halo),
            circleStrokeWidth(2.5f),
        ),
    )
}

/**
 * The camera that shows a whole area in the part of the map nothing covers.
 *
 * Worked out here rather than asked of the map's own bounds fitting, which adds
 * its padding to the padding the camera already carries and frames nothing. The
 * world is 512 density-independent pixels across at zoom 0 and doubles with each
 * level, and latitude is stretched as Mercator stretches it.
 */
private fun framing(area: CellEdges, view: MapView, inset: DoubleArray, density: Float): CameraPosition {
    val margin = AREA_MARGIN * 2.0
    val wide = view.width / density - (inset[0] + inset[2]) / density - margin
    val tall = view.height / density - (inset[1] + inset[3]) / density - margin
    fun mercator(latitude: Double): Double {
        val radians = Math.toRadians(latitude.coerceIn(-85.0, 85.0))
        return (1 - ln(tan(radians) + 1 / cos(radians)) / PI) / 2
    }
    val across = (area.east - area.west) / 360.0 * WORLD_DP
    val down = (mercator(area.south) - mercator(area.north)) * WORLD_DP
    val zoom = log2(min(wide.coerceAtLeast(1.0) / across, tall.coerceAtLeast(1.0) / down))
    val middle = (mercator(area.south) + mercator(area.north)) / 2
    val latitude = Math.toDegrees(atan(sinh(PI * (1 - 2 * middle))))
    return CameraPosition.Builder()
        .target(LatLng(latitude, (area.west + area.east) / 2))
        .zoom(zoom.coerceIn(0.0, START_ZOOM))
        .padding(inset)
        .build()
}

/** What the loaded style's sources say about where their data comes from. */
private fun declaredBy(style: Style): String = creditOf(style.sources.map { it.attribution })

/**
 * The credit line, small, on a plate of the page's own surface so it reads over
 * any map, with each name a link to its page.
 */
@Composable
private fun Credit(html: String, modifier: Modifier = Modifier) {
    val text = remember(html) {
        AnnotatedString.fromHtml(html, linkStyles = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline)))
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f), RoundedCornerShape(Radius.card))
            .padding(horizontal = Space.step1, vertical = 2.dp),
    )
}

/** Nothing of the place drawn, while an area is shown instead. */
private fun clearPlace(style: Style) {
    for (source in listOf(CELL, AROUND, FIX, DOT)) {
        style.getSourceAs<GeoJsonSource>(source)?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
    }
}

private fun draw(style: Style, selection: Selection) {
    style.getSourceAs<GeoJsonSource>(CELL)?.setGeoJson(outline(cellBox(selection.code)))
    style.getSourceAs<GeoJsonSource>(AROUND)
        ?.setGeoJson(FeatureCollection.fromFeatures(neighbourBoxes(selection.code).map(::outline)))

    // Only a device fix has an accuracy to draw. Anything else is exact for the
    // point it was given, and a disc around it would claim an error it has not got.
    val accuracy = selection.accuracyMetres
    val fromDevice = selection.source == Source.DEVICE && accuracy != null
    val point = selection.point
    style.getSourceAs<GeoJsonSource>(FIX)?.setGeoJson(
        if (fromDevice) FeatureCollection.fromFeature(disc(point, accuracy)) else FeatureCollection.fromFeatures(emptyList()),
    )
    style.getSourceAs<GeoJsonSource>(DOT)?.setGeoJson(
        if (fromDevice) FeatureCollection.fromFeature(Feature.fromGeometry(GeoPoint.fromLngLat(point.longitude, point.latitude)))
        else FeatureCollection.fromFeatures(emptyList()),
    )
}

/** A circle of a radius in metres, as a ring of 64 points, which is round enough at any zoom. */
private fun disc(centre: Point, metres: Double): Feature {
    val north = Math.toDegrees(metres / EARTH_METRES)
    val east = north / cos(Math.toRadians(centre.latitude))
    val ring = (0..64).map { step ->
        val angle = 2 * PI * step / 64
        GeoPoint.fromLngLat(centre.longitude + east * sin(angle), centre.latitude + north * cos(angle))
    }
    return Feature.fromGeometry(Polygon.fromLngLats(listOf(ring)))
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
 * stays put unless the place has left the screen. So does a device fix that is
 * already in view, which is what keeps a fix tightening from jolting the map
 * once a second. Anything arriving from elsewhere, a link or a typed code, is
 * flown to.
 */
private fun follow(map: MapLibreMap, selection: Selection, inset: DoubleArray, first: Boolean) {
    val target = LatLng(selection.point.latitude, selection.point.longitude)
    if (first) {
        map.cameraPosition = CameraPosition.Builder().target(target).zoom(START_ZOOM).padding(inset).build()
        return
    }
    // Counted against the uncovered part only: a place under the sheet is not in view.
    val local = selection.source == Source.MAP || selection.source == Source.NUDGE || selection.source == Source.DEVICE
    if (local && map.projection.getVisibleRegion(false).latLngBounds.contains(target)) return
    val zoom = max(map.cameraPosition.zoom, FOLLOW_ZOOM)
    map.easeCamera(
        CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(target).zoom(zoom).padding(inset).build()),
        700,
    )
}
