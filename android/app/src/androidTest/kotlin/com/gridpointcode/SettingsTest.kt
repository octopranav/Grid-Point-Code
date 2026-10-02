package com.gridpointcode

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gridpointcode.core.SPELLINGS
import com.gridpointcode.core.aloud
import com.gridpointcode.core.spellingFor
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The reader's choices, and that the app keeps them. */
@RunWith(AndroidJUnit4::class)
class SettingsTest {

    @get:Rule
    val rule = accessibleRule()

    @get:Rule
    val record = RecordOnFailure()

    private val reader = Reader(rule)

    @Test
    fun theBasemapChosenIsKept() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        reader.tap(R.string.tab_settings)
        reader.audit()
        reader.tap(R.string.basemap_fiord)
        rule.onNodeWithText(reader.text(R.string.basemap_fiord)).assertIsSelected()
        scenario.recreate()
        reader.tap(R.string.tab_settings)
        rule.onNodeWithText(reader.text(R.string.basemap_fiord)).assertIsSelected()
    }

    @Test
    fun theListenersLanguageChangesTheWordsTheCodeIsReadIn() {
        ActivityScenario.launch(MainActivity::class.java)
        reader.tap(R.string.tab_settings)
        val current = spellingFor(Locale.getDefault())
        rule.onNode(hasText(current.name, substring = true) and hasText(current.sample, substring = true)).assertIsDisplayed().performClick()
        reader.waitFor { rule.onNodeWithText(GERMAN.name) }
        reader.audit()
        rule.onNodeWithText(GERMAN.name).assertIsDisplayed().performClick()
        rule.waitForIdle()
        reader.tap(R.string.tab_map)
        reader.search(CODE)
        reader.waitFor { rule.onNode(androidx.compose.ui.test.hasContentDescription(aloud(CODE, GERMAN))) }
    }

    private companion object {
        const val CODE = "G3RJM8X3L1"

        /** German's own spelling table: Anton, Berta, Caesar. */
        val GERMAN = SPELLINGS.first { it.locale.language == "de" }
    }
}
