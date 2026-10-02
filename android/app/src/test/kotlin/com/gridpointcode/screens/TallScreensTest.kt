package com.gridpointcode.screens

import com.gridpointcode.R
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * A phone drawn very tall, so the whole of the panel and the whole of Settings
 * are each one image, in every condition that changes their length or their
 * direction: the dark theme, the largest text, a longer language, right to left.
 */
@Config(qualifiers = Devices.TALL)
class TallScreensTest : ScreenTest() {

    private fun recordPanel(fontScale: Float = 1f) {
        open(fontScale) { typed(Places.MARKET) }
        openSheet()
        record()
    }

    private fun recordSettings(fontScale: Float = 1f) {
        open(fontScale)
        tap(R.string.tab_settings)
        record()
    }

    @Test
    fun theWholePanel() = recordPanel()

    @Test
    @Config(qualifiers = "+night")
    fun theWholePanelInTheDark() = recordPanel()

    @Test
    fun theWholePanelInTheLargestText() = recordPanel(fontScale = 2f)

    @Test
    @Config(qualifiers = "+en-rXA")
    fun theWholePanelInALongerLanguage() = recordPanel()

    @Test
    @Config(qualifiers = "+ar-rXB")
    fun theWholePanelRightToLeft() = recordPanel()

    @Test
    fun settings() = recordSettings()

    @Test
    @Config(qualifiers = "+night")
    fun settingsInTheDark() = recordSettings()

    @Test
    fun settingsInTheLargestText() = recordSettings(fontScale = 2f)

    @Test
    @Config(qualifiers = "+en-rXA")
    fun settingsInALongerLanguage() = recordSettings()

    @Test
    @Config(qualifiers = "+ar-rXB")
    fun settingsRightToLeft() = recordSettings()
}
