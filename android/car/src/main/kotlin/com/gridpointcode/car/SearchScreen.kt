package com.gridpointcode.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.LongMessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import com.gridpointcode.core.Problem
import com.gridpointcode.core.formatted

/**
 * Go to a code: typed with the car's keyboard, which the car offers only while
 * it is parked. What is typed is read as the phone reads it, and the one place
 * it names, or why it names none, is shown as it is typed.
 */
class SearchScreen(
    carContext: CarContext,
    private val here: CarFix?,
    private val places: CarPlaces,
    private val speaker: CarSpeaker,
) : Screen(carContext) {

    private var answer: Typed? = null

    override fun onGetTemplate(): Template {
        val list = ItemList.Builder().setNoItemsMessage(carContext.getString(R.string.car_search_empty))
        when (val shown = answer) {
            null -> Unit
            is Typed.Place -> list.addItem(
                Row.Builder()
                    .setTitle(formatted(shown.selection.code))
                    .addText(carContext.getString(R.string.car_open))
                    .setOnClickListener { open(shown) }
                    .build(),
            )
            else -> list.addItem(Row.Builder().setTitle(why(shown)).build())
        }
        return SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) {
                answer = if (searchText.isBlank()) null else typed(searchText, here?.point)
                invalidate()
            }

            override fun onSearchSubmitted(searchText: String) {
                val read = typed(searchText, here?.point)
                answer = read
                if (read is Typed.Place) open(read) else invalidate()
            }
        })
            .setHeaderAction(Action.BACK)
            .setSearchHint(carContext.getString(R.string.car_search_hint))
            .setShowKeyboardByDefault(true)
            .setItemList(list.build())
            .build()
    }

    private fun open(place: Typed.Place) {
        screenManager.push(PlaceScreen(carContext, Shown.typed(place), here, places, speaker))
    }

    private fun why(answer: Typed): String = carContext.getString(
        when (answer) {
            Typed.NeedsFix -> R.string.car_needs_fix
            Typed.Area -> R.string.car_area
            Typed.Anchored -> R.string.car_anchored
            is Typed.Refused -> when (answer.problem) {
                is Problem.Closed -> R.string.car_closed
                is Problem.Unfollowed -> R.string.car_unfollowed
                is Problem.Reserved -> R.string.car_reserved
                else -> R.string.car_refused
            }
            is Typed.Place -> R.string.car_open
        },
    )
}

/**
 * The open-source notices, where the car is the only screen the app has. The
 * car shows a long message only while it is parked.
 */
class NoticesScreen(carContext: CarContext, private val notices: String) : Screen(carContext) {

    override fun onGetTemplate(): Template =
        LongMessageTemplate.Builder(notices)
            .setTitle(carContext.getString(R.string.car_notices))
            .setHeaderAction(Action.BACK)
            .build()
}
