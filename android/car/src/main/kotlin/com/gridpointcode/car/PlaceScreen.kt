package com.gridpointcode.car

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.versioning.CarAppApiLevels
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Distance
import androidx.car.app.model.DistanceSpan
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.gridpointcode.core.SavedPlace
import com.gridpointcode.core.Selection
import com.gridpointcode.core.Source
import com.gridpointcode.core.aloud
import com.gridpointcode.core.bearingBetween
import com.gridpointcode.core.formatted
import com.gridpointcode.core.metresBetween
import com.gridpointcode.core.selectionAt
import com.gridpointcode.core.selectionOf
import java.util.Locale

/**
 * One place, as the car shows it: where the car is, a saved place, or a code
 * typed in.
 *
 * @property here whether it is where the car is, which is read aloud but not driven to
 */
data class Shown(val selection: Selection, val name: String, val note: String, val here: Boolean, val metres: Int? = null) {
    companion object {
        fun here(fix: CarFix) = Shown(selectionAt(fix.point, Source.DEVICE), "", "", here = true, metres = fix.metres)

        fun saved(place: SavedPlace) = Shown(selectionOf(place.code, Source.SAVED), place.label, place.note, here = false)

        fun typed(place: Typed.Place) = Shown(place.selection, "", place.note, here = false)
    }
}

/**
 * The second screen, from the canvas: the code, how to say it, how far and
 * which way, and the two things to do with it: hand it to the driver's own
 * navigation app, or read it aloud.
 */
class PlaceScreen(
    carContext: CarContext,
    private val shown: Shown,
    private val from: CarFix?,
    private val places: CarPlaces,
    private val speaker: CarSpeaker,
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val code = shown.selection.code
        val written = formatted(code)
        val spelling = places.spelling()
        val pane = Pane.Builder().addRow(Row.Builder().setTitle(written).addText(aloud(code, spelling)).build())
        when {
            shown.here -> shown.metres?.let { metres ->
                pane.addRow(Row.Builder().setTitle(accuracyText(carContext, metres)).build())
            }
            from != null -> {
                // The car draws a distance on its own and nothing after it, so
                // the way and the directions to the door go on the line beneath.
                val point = shown.selection.point
                val way = directionText(carContext, bearingBetween(from.point, point))
                pane.addRow(
                    Row.Builder()
                        .setTitle(distanceText(metresBetween(from.point, point)))
                        .addText(if (shown.note.isEmpty()) way else "$way · ${shown.note}")
                        .build(),
                )
            }
            shown.note.isNotEmpty() -> pane.addRow(Row.Builder().setTitle(shown.note).build())
        }
        if (!shown.here) {
            pane.addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.car_navigate))
                    .setIcon(icon(carContext, R.drawable.ic_car_navigate))
                    .setBackgroundColor(brass(carContext))
                    .setOnClickListener(::navigate)
                    .build(),
            )
        }
        pane.addAction(
            Action.Builder()
                .setTitle(carContext.getString(R.string.car_read_aloud))
                .setIcon(icon(carContext, R.drawable.ic_car_speaker))
                .setOnClickListener { speaker.say(aloud(code, spelling), spelling.locale) }
                .build(),
        )
        val title = shown.name.ifEmpty { if (shown.here) carContext.getString(R.string.car_here) else written }
        val template = PaneTemplate.Builder(pane.build())
        // A header replaced the title and its action at level 7; an older car
        // takes them the old way.
        if (carContext.carAppApiLevel >= CarAppApiLevels.LEVEL_7) {
            template.setHeader(Header.Builder().setTitle(title).setStartHeaderAction(Action.BACK).build())
        } else {
            @Suppress("DEPRECATION")
            template.setTitle(title).setHeaderAction(Action.BACK)
        }
        return template.build()
    }

    /** Hands the place to whichever navigation app the driver uses, as a point: the code is this app's to read. */
    private fun navigate() {
        val point = shown.selection.point
        val address = Uri.parse(String.format(Locale.ROOT, "geo:%.6f,%.6f", point.latitude, point.longitude))
        runCatching { carContext.startCarApp(Intent(CarContext.ACTION_NAVIGATE, address)) }
            .onFailure { CarToast.makeText(carContext, R.string.car_no_navigation, CarToast.LENGTH_LONG).show() }
    }
}

/** A distance the car writes in the driver's own units: whole metres, or kilometres to a tenth. */
fun distanceText(metres: Double): SpannableStringBuilder {
    val distance = if (metres < 1000) {
        Distance.create(Math.round(metres).toDouble(), Distance.UNIT_METERS)
    } else {
        Distance.create(Math.round(metres / 100) / 10.0, Distance.UNIT_KILOMETERS)
    }
    return SpannableStringBuilder(" ").apply { setSpan(DistanceSpan.create(distance), 0, 1, Spanned.SPAN_INCLUSIVE_INCLUSIVE) }
}

/** An octant from the core, "NE", as the words for it. */
fun directionText(context: CarContext, octant: String): String = context.getString(
    when (octant) {
        "N" -> R.string.car_north
        "NE" -> R.string.car_north_east
        "E" -> R.string.car_east
        "SE" -> R.string.car_south_east
        "S" -> R.string.car_south
        "SW" -> R.string.car_south_west
        "W" -> R.string.car_west
        else -> R.string.car_north_west
    },
)

/** How far to trust a fix, said as the phone and the watch say it. */
fun accuracyText(context: CarContext, metres: Int): String =
    if (metres <= INSIDE_ONE_CELL) context.getString(R.string.car_accuracy_inside) else context.getString(R.string.car_accuracy, metres)

/** A fix this close is inside the one cell its code names. */
private const val INSIDE_ONE_CELL = 2

/**
 * The site's brass, day and night, from the palette's generated resources:
 * one name, with the night value under values-night, so each is read in its mode.
 */
fun brass(context: CarContext): CarColor {
    fun inMode(night: Int): Int {
        val configuration = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        }
        return ContextCompat.getColor(context.createConfigurationContext(configuration), R.color.brass)
    }
    return CarColor.createCustom(inMode(Configuration.UI_MODE_NIGHT_NO), inMode(Configuration.UI_MODE_NIGHT_YES))
}

fun icon(context: CarContext, drawable: Int): CarIcon = CarIcon.Builder(IconCompat.createWithResource(context, drawable)).build()
