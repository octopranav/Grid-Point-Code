package com.gridpointcode

import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app as readers' phones leave it: turned on its side, in the dark theme,
 * with the largest text, recreated by the system. Each keeps the place, and the
 * accessibility checks run in every one, which is where faint text in the dark
 * theme or a control pushed off the screen by large text shows up.
 */
@RunWith(AndroidJUnit4::class)
class ResilienceTest {

    @get:Rule
    val rule = accessibleRule()

    @get:Rule
    val record = RecordOnFailure()

    private val reader = Reader(rule)

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @After
    fun putTheDeviceBack() {
        reader.shell("cmd uimode night no")
        reader.shell("settings put system font_scale 1.0")
        device.setOrientationNatural()
        device.unfreezeRotation()
    }

    private fun openPlace(): ActivityScenario<MainActivity> {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        reader.search(CODE)
        reader.assertShowing(CODE)
        return scenario
    }

    @Test
    fun thePlaceSurvivesTheActivityBeingRecreated() {
        val scenario = openPlace()
        scenario.recreate()
        reader.assertShowing(CODE)
    }

    @Test
    fun onItsSideThePhoneUsesTheWideLayoutAndKeepsThePlace() {
        openPlace()
        // Turned as a reader turns it: the device rotates, the app follows.
        device.setOrientationLandscape()
        reader.assertShowing(CODE)
        // The wide layout's panel opens every section beside the map.
        rule.onNodeWithText(reader.text(R.string.nudge_title)).assertExists()
    }

    @Test
    fun theDarkThemeIsReadable() {
        reader.shell("cmd uimode night yes")
        openPlace()
        reader.audit()
    }

    @Test
    fun theDarkThemeIsReadableInTheWideLayout() {
        reader.shell("cmd uimode night yes")
        openPlace()
        device.setOrientationLandscape()
        reader.assertShowing(CODE)
    }

    @Test
    fun theLargestTextStillShowsThePlace() {
        reader.shell("settings put system font_scale 2.0")
        openPlace()
        reader.audit()
    }

    private companion object {
        const val CODE = "G3RJM8X3L1"
    }
}
