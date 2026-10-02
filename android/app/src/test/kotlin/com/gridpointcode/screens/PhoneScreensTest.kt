package com.gridpointcode.screens

import com.gridpointcode.R
import org.junit.Test
import org.robolectric.annotation.Config

/** A phone: the card at rest over the map, the dialogs, the emergency card and the saved list. */
@Config(qualifiers = Devices.PHONE)
class PhoneScreensTest : ScreenTest() {

    @Test
    fun aPlaceOnTheCard() {
        open { typed(Places.MARKET) }
        record()
    }

    @Test
    @Config(qualifiers = "+night")
    fun aPlaceInTheDark() {
        open { typed(Places.MARKET) }
        record()
    }

    @Test
    fun aPlaceInTheLargestText() {
        open(fontScale = 2f) { typed(Places.MARKET) }
        record()
    }

    @Test
    @Config(qualifiers = "+ar-rXB")
    fun aPlaceRightToLeft() {
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
    fun aCodeWhoseCheckDoesNotMatch() {
        // Through Go, as the search field sends it, where it was once looked up as a name.
        open { go(Places.MISCHECKED) }
        record()
    }

    @Test
    fun savingAPlace() {
        open { typed(Places.MARKET) }
        recordTyping { tapDescribed(R.string.save_place) }
    }

    @Test
    fun theQrCode() {
        open { typed(Places.MARKET) }
        tap(R.string.qr_button)
        record()
    }

    @Test
    fun theEmergencyCard() {
        open { openEmergency() }
        foundAt(Places.UNION_LATITUDE, Places.UNION_LONGITUDE)
        record()
    }

    @Test
    @Config(qualifiers = "+night")
    fun theEmergencyCardInTheDark() {
        open { openEmergency() }
        foundAt(Places.UNION_LATITUDE, Places.UNION_LONGITUDE)
        record()
    }

    /** The coordinates a dispatcher is read, which must not change order in a right-to-left language. */
    @Test
    @Config(qualifiers = "+ar-rXB")
    fun theEmergencyCardRightToLeft() {
        open { openEmergency() }
        foundAt(Places.UNION_LATITUDE, Places.UNION_LONGITUDE)
        record()
    }

    @Test
    fun savedPlacesNearestFirst() {
        open { threeSaved() }
        tap(R.string.tab_saved)
        record()
    }

    @Test
    @Config(qualifiers = "+night")
    fun savedPlacesInTheDark() {
        open { threeSaved() }
        tap(R.string.tab_saved)
        record()
    }

    @Test
    @Config(qualifiers = "+en-rXA")
    fun savedPlacesInALongerLanguage() {
        open { threeSaved() }
        tap(R.string.tab_saved)
        record()
    }

    /** Arrows point at compass bearings, which do not turn round with the language. */
    @Test
    @Config(qualifiers = "+ar-rXB")
    fun savedPlacesRightToLeft() {
        open { threeSaved() }
        tap(R.string.tab_saved)
        record()
    }

    @Test
    fun nothingSavedYet() {
        open()
        tap(R.string.tab_saved)
        record()
    }
}
