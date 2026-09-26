package com.gridpointcode.car

import android.content.pm.PackageManager
import android.location.Location
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarLocation
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Metadata
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceListMapTemplate
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.gridpointcode.core.Point
import com.gridpointcode.core.SavedOrder
import com.gridpointcode.core.SavedPlace
import com.gridpointcode.core.SavedRow
import com.gridpointcode.core.Source
import com.gridpointcode.core.formatted
import com.gridpointcode.core.savedRows
import com.gridpointcode.core.selectionAt
import com.gridpointcode.core.selectionOf
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Where the car is, and how far to trust it, in whole metres. */
data class CarFix(val point: Point, val metres: Int) {
    val code: String get() = selectionAt(point, Source.DEVICE).code
}

/**
 * The first screen, from the canvas: where the car is, then the saved places,
 * nearest first, each numbered on the car's own map. The car draws the map;
 * this names the places on it.
 *
 * The map holds only rows that say how far away they are, so it is shown once
 * there are saved places and a fix to measure them from. Until then, and in a
 * car running Android Automotive, which has no saved places to put on a map,
 * the same rows are a list. A row the map refuses does not fail quietly: the
 * car shows its placeholder for a missing maps app instead of the whole screen.
 *
 * Where the car is, is asked only while this screen shows, and it is redrawn
 * when the code changes or ten seconds have passed, not with every fix: a car
 * limits how often an app may redraw.
 */
class PlacesScreen(
    carContext: CarContext,
    private val places: CarPlaces,
    private val speaker: CarSpeaker,
) : Screen(carContext) {

    private val fixes = CarFixes(carContext)

    /** Counts each time finding the car is started again: a permission given, or location switched on. */
    private val attempts = MutableStateFlow(0)
    private var here: CarFix? = null
    private var saved: List<SavedPlace> = places.saved.value
    private var drawnAt = 0L

    init {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    places.saved.collect {
                        saved = it
                        invalidate()
                    }
                }
                launch {
                    attempts.collectLatest { if (fixes.permitted() && fixes.switchedOn()) fixes.fixes().collect(::arrived) }
                }
            }
        }
    }

    private fun arrived(location: Location) {
        val fix = CarFix(Point(location.latitude, location.longitude), location.accuracy.roundToInt())
        val before = here
        here = fix
        val now = System.currentTimeMillis()
        if (before == null || before.code != fix.code || now - drawnAt >= REDRAW_MS) invalidate()
    }

    override fun onGetTemplate(): Template {
        drawnAt = System.currentTimeMillis()
        val mapped = places.savedHere && here != null
        val limit = runCatching {
            carContext.getCarService(ConstraintManager::class.java).getContentLimit(
                if (mapped) ConstraintManager.CONTENT_LIMIT_TYPE_PLACE_LIST else ConstraintManager.CONTENT_LIMIT_TYPE_LIST,
            )
        }.getOrDefault(DEFAULT_LIMIT)
        val list = ItemList.Builder().addItem(hereRow(browsable = mapped))
        val rows = savedRows(saved, here?.point, if (here != null) SavedOrder.NEAREST else SavedOrder.RECENT)
        val room = limit - 1 - if (places.savedHere) 0 else 1
        rows.take(room).forEachIndexed { index, row -> list.addItem(savedRow(index + 1, row)) }
        if (!places.savedHere) list.addItem(Row.Builder().setTitle(carContext.getString(R.string.car_saved_on_phone)).build())

        val strip = ActionStrip.Builder().addAction(
            Action.Builder()
                .setIcon(icon(carContext, R.drawable.ic_car_search))
                .setOnClickListener { screenManager.push(SearchScreen(carContext, here, places, speaker)) }
                .build(),
        )
        places.notices?.let { notices ->
            strip.addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.car_notices_short))
                    .setOnClickListener { screenManager.push(NoticesScreen(carContext, notices(carContext))) }
                    .build(),
            )
        }
        if (!mapped) {
            return ListTemplate.Builder()
                .setTitle(carContext.getString(R.string.car_title))
                .setHeaderAction(Action.APP_ICON)
                .setSingleList(list.build())
                .setActionStrip(strip.build())
                .build()
        }
        return PlaceListMapTemplate.Builder()
            .setTitle(carContext.getString(R.string.car_title))
            .setHeaderAction(Action.APP_ICON)
            .setCurrentLocationEnabled(fixes.permitted())
            .setItemList(list.build())
            .setActionStrip(strip.build())
            .build()
    }


    /**
     * Where the car is. On the map it opens its own screen as a browsable row,
     * since the map holds only rows with a distance, and this one is here.
     */
    private fun hereRow(browsable: Boolean): Row {
        val row = Row.Builder().setTitle(carContext.getString(R.string.car_here)).setBrowsable(browsable)
        val fix = here
        when {
            !fixes.permitted() -> row.addText(carContext.getString(R.string.car_allow)).setOnClickListener {
                carContext.requestPermissions(LOCATION) { _, _ -> tryAgain() }
            }
            !fixes.switchedOn() -> row.addText(carContext.getString(offWhere())).setOnClickListener(::tryAgain)
            fix == null -> row.addText(carContext.getString(R.string.car_finding))
            else -> row.addText(formatted(fix.code)).setOnClickListener {
                screenManager.push(PlaceScreen(carContext, Shown.here(fix), here, places, speaker))
            }
        }
        return row.build()
    }

    private fun tryAgain() {
        attempts.value += 1
        invalidate()
    }

    /**
     * Where location is switched on: in a car running Android Automotive, the
     * car's own settings; on Android Auto, the phone's, which is what is asked.
     */
    private fun offWhere(): Int =
        if (carContext.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) R.string.car_location_off_car
        else R.string.car_location_off_phone

    private fun savedRow(number: Int, row: SavedRow): Row {
        val point = selectionOf(row.place.code, Source.SAVED).point
        val written = formatted(row.place.code)
        val saved = Row.Builder().setTitle(row.place.label.ifEmpty { written })
        // The car draws a distance on its own line and nothing after it, so
        // the code has a line of its own beneath.
        row.metres?.let { saved.addText(distanceText(it)) }
        return saved
            .addText(written)
            .setMetadata(
                Metadata.Builder()
                    .setPlace(
                        Place.Builder(CarLocation.create(point.latitude, point.longitude))
                            .setMarker(PlaceMarker.Builder().setLabel(number.toString()).setColor(brass(carContext)).build())
                            .build(),
                    )
                    .build(),
            )
            .setOnClickListener { screenManager.push(PlaceScreen(carContext, Shown.saved(row.place), here, places, speaker)) }
            .build()
    }

    private companion object {
        const val REDRAW_MS = 10_000L

        /** What a place list may hold when the car does not say: the fewest any car allows. */
        const val DEFAULT_LIMIT = 6
    }
}
