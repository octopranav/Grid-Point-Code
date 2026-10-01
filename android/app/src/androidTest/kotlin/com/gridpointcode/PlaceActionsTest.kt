package com.gridpointcode

import android.app.Activity
import android.app.Instrumentation
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.espresso.intent.matcher.IntentMatchers.isInternal
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gridpointcode.core.Compass
import com.gridpointcode.core.around
import com.gridpointcode.core.formatted
import com.gridpointcode.core.addressOf
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** What a reader does with a place once it is on the card. */
@RunWith(AndroidJUnit4::class)
class PlaceActionsTest {

    @get:Rule
    val rule = accessibleRule()

    @get:Rule
    val record = RecordOnFailure()

    private val reader = Reader(rule)

    @Before
    fun open() {
        ActivityScenario.launch(MainActivity::class.java)
        reader.search(CODE)
        reader.assertShowing(CODE)
    }

    @Test
    fun everyDirectionOnThePadMovesOneCellThatWay() {
        for (direction in Compass.entries) {
            reader.search(CODE)
            reader.assertShowing(CODE)
            val target = around(CODE).getValue(direction)
            // The pad sits below the card, and writes each neighbour as the
            // five characters that differ.
            reader.reach(rule.onNodeWithText("-" + target.takeLast(5)))
            reader.assertShowing(target)
        }
        rule.onNodeWithText(reader.text(R.string.source_nudge), ignoreCase = true).assertExists()
    }

    @Test
    fun theQrCodeCarriesThePlacesLink() {
        rule.onAllNodesWithText(reader.text(R.string.qr_button)).onFirst().assertIsDisplayed().performClick()
        reader.waitFor { rule.onNodeWithText(reader.text(R.string.qr_place)) }
        // The address stands in the dialog and in the panel behind it.
        reader.waitForText(addressOf(CODE))
        reader.audit()
    }

    @Test
    fun shareHandsTheCodeAndItsLinkToTheAppTheReaderChooses() {
        Intents.init()
        try {
            intending(not(isInternal())).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
            rule.onAllNodesWithText(reader.text(R.string.share)).onFirst().assertIsDisplayed().performClick()
            intended(
                allOf(
                    hasAction(Intent.ACTION_CHOOSER),
                    hasExtra(
                        equalTo(Intent.EXTRA_INTENT),
                        allOf(
                            hasAction(Intent.ACTION_SEND),
                            hasExtra(equalTo(Intent.EXTRA_TEXT), containsString(addressOf(CODE))),
                        ),
                    ),
                ),
            )
        } finally {
            Intents.release()
        }
    }

    @Test
    fun copyPutsTheCodeOnTheClipboard() {
        rule.onAllNodesWithText(reader.text(R.string.copy)).onFirst().assertIsDisplayed().performClick()
        rule.waitForIdle()
        var copied: String? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val clipboard = reader.context.getSystemService(ClipboardManager::class.java)
            copied = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        }
        assertEquals(formatted(CODE), copied)
    }

    private companion object {
        /** St Lawrence Market's north door, in Toronto. */
        const val CODE = "G3RJM8X3L1"
    }
}
