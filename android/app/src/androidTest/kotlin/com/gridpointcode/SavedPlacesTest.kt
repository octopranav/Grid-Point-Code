package com.gridpointcode

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Saving a place, finding it again, and letting it go. */
@RunWith(AndroidJUnit4::class)
class SavedPlacesTest {

    @get:Rule
    val rule = accessibleRule()

    @get:Rule
    val record = RecordOnFailure()

    @get:Rule
    val location: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    private val reader = Reader(rule)

    @Before
    fun launch() {
        ActivityScenario.launch(MainActivity::class.java)
    }

    private fun save(code: String, name: String) {
        reader.search(code)
        reader.assertShowing(code)
        // Each place here is kilometres from the last, so the app asks whether
        // the code was heard right; a reader saving it meant it.
        reader.keepIfDoubted(code)
        reader.tapDescribed(R.string.save_place)
        reader.waitFor { rule.onNode(hasSetTextAction() and hasText(reader.text(R.string.save_label), substring = true)) }
        rule.onNode(hasSetTextAction() and hasText(reader.text(R.string.save_label), substring = true)).performTextInput(name)
        reader.audit()
        reader.tap(R.string.save_confirm)
        reader.waitForKeyboardToClose()
        reader.waitFor { rule.onNodeWithContentDescription(reader.text(R.string.saved_edit)) }
    }

    @Test
    fun aSavedPlaceIsListedWithItsNameAndOpensAgain() {
        save(MARKET, "Market, north door")
        reader.tap(R.string.tab_saved)
        reader.waitFor { rule.onNodeWithText("Market, north door") }
        reader.audit()
        rule.onNodeWithText("Market, north door").assertIsDisplayed().performClick()
        reader.assertShowing(MARKET)
        rule.onNodeWithText(reader.text(R.string.source_saved), ignoreCase = true).assertExists()
    }

    @Test
    fun aSavedPlaceIsWrittenToTheAppsOwnStorage() {
        save(MARKET, "Market, north door")
        val file = File(reader.context.filesDir, "saved-places.json")
        rule.waitUntil(5_000) { file.exists() && file.readText().contains("Market, north door") }
        assertTrue(file.readText().contains(MARKET))
    }

    @Test
    fun aRemovedPlaceIsGone() {
        save(MARKET, "Market, north door")
        reader.tapDescribed(R.string.saved_edit)
        reader.tap(R.string.save_remove)
        reader.waitFor { rule.onNodeWithContentDescription(reader.text(R.string.save_place)) }
        reader.tap(R.string.tab_saved)
        reader.waitFor { rule.onNodeWithText(reader.text(R.string.saved_empty)) }
        rule.onNodeWithText("Market, north door").assertDoesNotExist()
    }

    @Test
    fun nearestFirstMeasuresFromWhereTheReaderIs() {
        save(KENSINGTON, "Kensington Market")
        save(MARKET, "Market, north door")
        save(TOWER, "Tower base")
        // Standing at Union Station: the tower is nearest, then the market, then Kensington.
        reader.fix(43.64546, -79.38063)
        reader.tapDescribed(R.string.locate)
        // Straight on to Saved, before the fix is in: it arrives there, and the
        // list stays, measured from the reader, rather than giving way to the map.
        reader.tap(R.string.tab_saved)
        reader.waitFor { rule.onNodeWithText(reader.text(R.string.saved_from_you), substring = true) }
        val top = listOf("Tower base", "Market, north door", "Kensington Market").map { name ->
            rule.onNodeWithText(name).fetchSemanticsNode().boundsInRoot.top
        }
        assertTrue("nearest first: $top", top == top.sorted())
    }

    private companion object {
        const val MARKET = "G3RJM8X3L1"
        const val KENSINGTON = "G3RJL5FRCR"
        const val TOWER = "G3RJM0M67J"
    }
}
