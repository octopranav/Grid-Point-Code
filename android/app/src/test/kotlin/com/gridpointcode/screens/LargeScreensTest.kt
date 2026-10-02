package com.gridpointcode.screens

import com.gridpointcode.R
import org.junit.Test
import org.robolectric.annotation.Config

/** A tablet on its side, where the panel stands beside the map. */
@Config(qualifiers = Devices.TABLET)
class TabletScreensTest : ScreenTest() {

    @Test
    fun aPlaceBesideTheMap() {
        open { typed(Places.MARKET) }
        record()
    }

    @Test
    @Config(qualifiers = "+night")
    fun aPlaceBesideTheMapInTheDark() {
        open { typed(Places.MARKET) }
        record()
    }

    @Test
    fun aCodeHeardWrong() {
        open {
            typed(Places.KENSINGTON)
            typed(Places.MARKET)
        }
        record()
    }

    @Test
    fun savedPlaces() {
        open { threeSaved() }
        tap(R.string.tab_saved)
        record()
    }
}

/** A foldable opened out: nearly square, and just short of the wide layout. */
@Config(qualifiers = Devices.FOLDABLE)
class FoldableScreensTest : ScreenTest() {

    @Test
    fun aPlaceOnTheCard() {
        open { typed(Places.MARKET) }
        record()
    }

    @Test
    fun savingAPlace() {
        open { typed(Places.MARKET) }
        recordTyping { tapDescribed(R.string.save_place) }
    }
}

/** A small, older phone, where everything is tightest. */
@Config(qualifiers = Devices.SMALL)
class SmallPhoneScreensTest : ScreenTest() {

    @Test
    fun aPlaceOnTheCard() {
        open { typed(Places.MARKET) }
        record()
    }

    @Test
    fun savingAPlace() {
        open { typed(Places.MARKET) }
        recordTyping { tapDescribed(R.string.save_place) }
    }

    @Test
    fun theEmergencyCard() {
        open { openEmergency() }
        foundAt(Places.UNION_LATITUDE, Places.UNION_LONGITUDE)
        record()
    }
}
