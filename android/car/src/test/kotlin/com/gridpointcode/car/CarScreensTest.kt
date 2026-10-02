package com.gridpointcode.car

import android.content.Context
import android.os.Looper
import androidx.car.app.CarContext
import androidx.car.app.HandshakeInfo
import androidx.car.app.versioning.CarAppApiLevels
import androidx.car.app.OnDoneCallback
import androidx.car.app.model.Action
import androidx.car.app.model.Distance
import androidx.car.app.model.DistanceSpan
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.testing.TestCarContext
import androidx.car.app.testing.TestScreenManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gridpointcode.core.Point
import com.gridpointcode.core.SavedPlace
import com.gridpointcode.core.Source
import com.gridpointcode.core.aloud
import com.gridpointcode.core.formatted
import com.gridpointcode.core.metresBetween
import com.gridpointcode.core.selectionOf
import com.gridpointcode.core.spellingFor
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * The car's screens as the car is handed them: templates, checked row by row
 * and action by action on the Car App Library's own test car. A car draws a
 * template in its own style and refuses one that breaks its rules, so what is
 * checked is what each screen says and does, and what it hands to other apps.
 */
@RunWith(AndroidJUnit4::class)
class CarScreensTest {

    private val car = carAt(CarAppApiLevels.LEVEL_7)

    /** A test car that has shaken hands at [level], as a real one does before it asks for a template. */
    private fun carAt(level: Int) = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext()).apply {
        updateHandshakeInfo(HandshakeInfo("com.gridpointcode.test.host", level))
    }
    private val speaker by lazy { CarSpeaker(car) }
    private val spelling = spellingFor(Locale.ENGLISH)

    private val market = SavedPlace("G3RJM8X3L1", "Market, north door", "North door, beside the bakery stall", savedAt = 1)
    private val kensington = SavedPlace("G3RJL5FRCR", "Kensington Market", "", savedAt = 2)

    /** The car at Union Station, to within four metres. */
    private val union = CarFix(Point(43.64546, -79.38063), 4)

    private fun places(
        vararg saved: SavedPlace,
        savedHere: Boolean = true,
        notices: ((Context) -> String)? = null,
    ) = CarPlaces(MutableStateFlow(saved.toList()), { spelling }, savedHere, notices)

    private fun Row.lines(): List<String> = listOf(title.toString()) + texts.map { it.toString() }

    private fun click(action: Action) = sent { action.onClickDelegate!!.sendClick(it) }

    private fun click(row: Row) = sent { row.onClickDelegate!!.sendClick(it) }

    /** Sends a host's call into the app and lets the main thread run it, as the car's would. */
    private fun sent(call: (OnDoneCallback) -> Unit) {
        call(object : OnDoneCallback {})
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun aSavedPlaceSaysItsCodeHowFarWhichWayAndHowToFindTheDoor() {
        val template = PlaceScreen(car, Shown.saved(market), union, places(market), speaker).onGetTemplate() as PaneTemplate
        val rows = template.pane.rows
        assertEquals(listOf(formatted(market.code), aloud(market.code, spelling)), rows[0].lines())
        // The car writes the distance itself, from a span, in the driver's units.
        val far = rows[1].title!!.spans.single().carSpan as DistanceSpan
        val metres = metresBetween(union.point, selectionOf(market.code, Source.SAVED).point)
        assertTrue("the market is under a kilometre from the station: $metres", metres < 1000)
        assertEquals(Distance.UNIT_METERS, far.distance.displayUnit)
        assertEquals(Math.round(metres).toDouble(), far.distance.displayDistance, 0.0)
        assertEquals("north-east · North door, beside the bakery stall", rows[1].texts.single().toString())
        assertEquals(listOf("Navigate", "Read aloud"), template.pane.actions.map { it.title.toString() })
        assertEquals("Market, north door", template.header?.title?.toString() ?: template.title.toString())
    }

    @Test
    fun navigateHandsThePointToTheDriversOwnNavigationApp() {
        val template = PlaceScreen(car, Shown.saved(market), union, places(market), speaker).onGetTemplate() as PaneTemplate
        click(template.pane.actions.first { it.title.toString() == "Navigate" })
        val sent = car.startCarAppIntents.single()
        val point = selectionOf(market.code, Source.SAVED).point
        assertEquals(CarContext.ACTION_NAVIGATE, sent.action)
        assertEquals(String.format(Locale.ROOT, "geo:%.6f,%.6f", point.latitude, point.longitude), sent.dataString)
    }

    @Test
    fun whereTheCarIsSaysHowCloseAndOffersNoWayToNavigateThere() {
        val template = PlaceScreen(car, Shown.here(union), union, places(), speaker).onGetTemplate() as PaneTemplate
        assertEquals(formatted(union.code), template.pane.rows[0].title.toString())
        assertEquals("±4 m", template.pane.rows[1].title.toString())
        assertEquals(listOf("Read aloud"), template.pane.actions.map { it.title.toString() })
        assertEquals("Where am I", template.header?.title?.toString() ?: template.title.toString())
    }

    @Test
    fun beforeAFixTheSavedPlacesAreAListNewestFirstUnderWhereTheCarIs() {
        // As the shelf keeps them: the newest first.
        val template = PlacesScreen(car, places(kensington, market), speaker).onGetTemplate() as ListTemplate
        val rows = template.singleList!!.items.map { it as Row }
        assertEquals("Where am I", rows[0].title.toString())
        assertEquals("Allow location to find where the car is.", rows[0].texts.single().toString())
        assertEquals(listOf("Kensington Market", "Market, north door"), rows.drop(1).map { it.title.toString() })
        assertEquals(formatted(market.code), rows[2].texts.last().toString())
    }

    @Test
    fun anOlderCarGetsTheTitleTheWayItKnows() {
        val older = carAt(CarAppApiLevels.LEVEL_6)
        val template = PlaceScreen(older, Shown.saved(market), union, places(market), CarSpeaker(older)).onGetTemplate() as PaneTemplate
        // Built the way a car before level 7 takes it, with the title and the
        // header action set on their own, and without asking for a header.
        @Suppress("DEPRECATION")
        assertEquals("Market, north door", template.title.toString())
    }

    @Test
    fun aSavedPlaceOpensItsOwnScreen() {
        val template = PlacesScreen(car, places(market), speaker).onGetTemplate() as ListTemplate
        click(template.singleList!!.items[1] as Row)
        val pushed = car.getCarService(TestScreenManager::class.java).screensPushed.single()
        val opened = (pushed as PlaceScreen).onGetTemplate() as PaneTemplate
        assertEquals(formatted(market.code), opened.pane.rows[0].title.toString())
    }

    @Test
    fun aCarWithNoPhoneSaysWhereTheSavedPlacesAre() {
        val template = PlacesScreen(car, places(savedHere = false, notices = { "The notices." }), speaker).onGetTemplate() as ListTemplate
        val rows = template.singleList!!.items.map { it as Row }
        assertEquals("Saved places come from the phone app, with Android Auto.", rows.last().title.toString())
        val actions = template.actionStrip!!.actions
        assertEquals(2, actions.size)
        assertEquals("Notices", actions.last().title.toString())
    }

    @Test
    fun aCodeTypedIsOfferedAsItIsTypedAndOpensWhenSent() {
        val screen = SearchScreen(car, union, places(), speaker)
        val empty = screen.onGetTemplate() as SearchTemplate
        assertEquals("The place appears here as it is typed.", empty.itemList!!.noItemsMessage.toString())
        sent { empty.searchCallbackDelegate.sendSearchTextChanged("g3rjm 8x3l1", it) }
        val offered = (screen.onGetTemplate() as SearchTemplate).itemList!!.items.single() as Row
        assertEquals(listOf(formatted(market.code), "Open"), offered.lines())
        sent { empty.searchCallbackDelegate.sendSearchSubmitted("g3rjm 8x3l1", it) }
        assertTrue(car.getCarService(TestScreenManager::class.java).screensPushed.single() is PlaceScreen)
    }

    @Test
    fun aShortFormWaitsForAFixRatherThanNamingADoorElsewhere() {
        val screen = SearchScreen(car, null, places(), speaker)
        val template = screen.onGetTemplate() as SearchTemplate
        sent { template.searchCallbackDelegate.sendSearchTextChanged("-8X3L1", it) }
        val row = (screen.onGetTemplate() as SearchTemplate).itemList!!.items.single() as Row
        assertEquals("A short form is read against where the car is, which is not known yet.", row.title.toString())
    }
}
