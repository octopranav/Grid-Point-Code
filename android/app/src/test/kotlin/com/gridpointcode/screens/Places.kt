package com.gridpointcode.screens

import com.gridpointcode.PlaceViewModel
import com.gridpointcode.core.Source

/** Places in Toronto the screens are drawn at, all in the landmark shard the tests carry. */
object Places {
    /** St Lawrence Market's north door. */
    const val MARKET = "G3RJM8X3L1"

    /** Kensington Market, 2.4 km from the market: far enough to be doubted when typed after it. */
    const val KENSINGTON = "G3RJL5FRCR"

    /** The foot of the CN Tower. */
    const val TOWER = "G3RJM0M67J"

    /** The market's code with a check character that does not match it. */
    const val MISCHECKED = "#G3RJM-8X3L1*Q"

    /** Where the emergency card finds the reader: Union Station. */
    const val UNION_LATITUDE = 43.64546
    const val UNION_LONGITUDE = -79.38063
}

/** A code typed, as the search field hands it on. */
fun PlaceViewModel.typed(code: String) = open(code, Source.CODE)

/**
 * Three places saved, each named and one with directions, then the market shown
 * again. Each is kilometres from the last, so each is kept when doubted, as a
 * reader saving it meant it.
 */
fun PlaceViewModel.threeSaved() {
    typed(Places.KENSINGTON)
    save("Kensington Market", "")
    typed(Places.TOWER)
    keep()
    save("Tower base", "")
    typed(Places.MARKET)
    keep()
    save("Market, north door", "North door, beside the bakery stall")
}
