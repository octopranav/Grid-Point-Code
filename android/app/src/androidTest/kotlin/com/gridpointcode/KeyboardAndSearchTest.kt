package com.gridpointcode

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gridpointcode.core.Compass
import com.gridpointcode.core.around
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A keyboard and a mouse, for Android on a desktop; and searching by name, online and off. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class KeyboardAndSearchTest {

    @get:Rule
    val rule = accessibleRule()

    @get:Rule
    val record = RecordOnFailure()

    private val reader = Reader(rule)

    @After
    fun reconnect() {
        reader.shell("cmd connectivity airplane-mode disable")
    }

    @Test
    fun ctrlKGoesToTheSearchField() {
        ActivityScenario.launch(MainActivity::class.java)
        rule.waitForIdle()
        rule.onRoot().performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.K)
            keyUp(Key.CtrlLeft)
        }
        rule.onNode(hasSetTextAction() and hasText(reader.text(R.string.search_label), substring = true)).assertIsFocused()
    }

    @Test
    fun theArrowKeysNudgeThePlace() {
        ActivityScenario.launch(MainActivity::class.java)
        reader.search(CODE)
        reader.assertShowing(CODE)
        // Out of the field, as Go leaves it, so the keys reach the screen.
        rule.onRoot().performKeyInput { pressKey(Key.Escape) }
        rule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        reader.assertShowing(around(CODE).getValue(Compass.N))
    }

    @Test
    fun aNameListsThePlacesItCouldMeanLargestFirst() {
        ActivityScenario.launch(MainActivity::class.java)
        reader.search("toronto", go = false)
        reader.waitFor(20_000) { rule.onNodeWithText("Ontario, Canada") }
        reader.audit()
    }

    @Test
    fun withNoConnectionANameFindsThePlacesAlreadyOnThePhone() {
        // The example a fresh install opens on is in Toronto, so its landmarks are on the phone.
        ActivityScenario.launch(MainActivity::class.java)
        reader.shell("cmd connectivity airplane-mode enable")
        reader.search("toronto", go = false)
        reader.waitFor(20_000) { rule.onNodeWithText(reader.text(R.string.names_local)) }
        reader.waitForText("Toronto Union Station", substring = true)
        reader.audit()
    }

    @Test
    fun withNoConnectionANameFarAwaySaysWhatStillWorks() {
        ActivityScenario.launch(MainActivity::class.java)
        reader.shell("cmd connectivity airplane-mode enable")
        reader.search("nairobi", go = false)
        reader.waitFor(20_000) { rule.onNodeWithText(reader.text(R.string.names_offline)) }
        reader.audit()
        // A code still works with no connection.
        reader.search(CODE)
        reader.assertShowing(CODE)
    }

    private companion object {
        const val CODE = "G3RJM8X3L1"
    }
}
