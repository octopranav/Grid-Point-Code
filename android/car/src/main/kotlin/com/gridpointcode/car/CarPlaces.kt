package com.gridpointcode.car

import android.content.Context
import com.gridpointcode.core.SavedPlace
import com.gridpointcode.core.Spelling
import kotlinx.coroutines.flow.StateFlow

/**
 * What an app hands its car screens.
 *
 * On Android Auto the car screens run in the phone app, so they have the
 * phone's saved places and the listener's language as the phone keeps them.
 * An Android Automotive car runs its own app with no phone, so it has neither,
 * and says where the saved places are instead.
 *
 * @property saved the saved places, or none in a car that has no phone
 * @property spelling the words a code is read aloud in
 * @property savedHere whether saved places can reach this car at all
 * @property notices the open-source notices, where the car is the only screen the app has
 */
class CarPlaces(
    val saved: StateFlow<List<SavedPlace>>,
    val spelling: () -> Spelling,
    val savedHere: Boolean,
    val notices: ((Context) -> String)?,
)
