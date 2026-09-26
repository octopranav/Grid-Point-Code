package com.gridpointcode

import com.gridpointcode.car.CarPlaces
import com.gridpointcode.car.GpcCarAppService

/**
 * Android Auto: the car screens, run by the phone app on the phone and shown on
 * the car's display. They have the phone's saved places and the listener's
 * language as the phone keeps them; the notices are on the phone's own page.
 */
class PhoneCarService : GpcCarAppService() {

    override fun places(): CarPlaces {
        SavedShelf.open(this)
        val preferences = Preferences(this)
        return CarPlaces(
            saved = SavedShelf.saved,
            spelling = preferences::listener,
            savedHere = true,
            notices = null,
        )
    }
}
