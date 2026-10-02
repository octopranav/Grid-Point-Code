package com.gridpointcode

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.gridpointcode.core.formatted
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every way a place reaches the app: typed, linked, handed over by another app,
 * selected as text, and the launcher's emergency shortcut. Each goes through the
 * one reader, so each must land on the same place with the same card.
 */
@RunWith(AndroidJUnit4::class)
class ArrivalTest {

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

    private fun launch(intent: Intent? = null): ActivityScenario<MainActivity> =
        if (intent == null) ActivityScenario.launch(MainActivity::class.java)
        else ActivityScenario.launch(intent.setClass(reader.context, MainActivity::class.java))

    @Test
    fun aFreshInstallShowsTheSpecificationsExample() {
        launch()
        reader.waitFor { rule.onNodeWithText(reader.text(R.string.source_sample), ignoreCase = true) }
        reader.assertShowing(SAMPLE)
    }

    @Test
    fun aTypedCodeOpens() {
        launch()
        reader.search("G3RJM-8X3L1")
        reader.assertShowing("G3RJM8X3L1")
        rule.onNodeWithText(reader.text(R.string.source_code), ignoreCase = true).assertExists()
    }

    @Test
    fun aCodeTypedCarelesslyIsReadAsTheSameCode() {
        launch()
        // Lower case, no hash, spaces where the hyphen goes: read as written.
        reader.search("g3rjm 8x3l1")
        reader.assertShowing("G3RJM8X3L1")
    }

    /**
     * St Lawrence Market is 2.4 km from Kensington Market, far enough from the
     * place on screen that the app offers the codes one slip away from the one
     * typed, in case a character was heard wrong when it was read out.
     */
    private fun typeAFarCode() {
        launch()
        reader.search(KENSINGTON)
        reader.assertShowing(KENSINGTON)
        reader.search(MARKET)
        reader.waitFor { rule.onNodeWithText(reader.text(R.string.doubt_label), ignoreCase = true) }
        reader.audit()
    }

    @Test
    fun aCodeFarFromThePlaceOnScreenIsKeptWhenTheReaderMeantIt() {
        typeAFarCode()
        reader.reach(rule.onNodeWithText(reader.text(R.string.doubt_keep, formatted(MARKET))))
        rule.onNodeWithText(reader.text(R.string.doubt_label), ignoreCase = true).assertDoesNotExist()
        reader.assertShowing(MARKET)
    }

    @Test
    fun aCodeFarFromThePlaceOnScreenOffersTheSlipThatWasMeant() {
        typeAFarCode()
        // One character heard wrong: the sixth, a 9 heard as an 8, 863 m from Kensington.
        reader.reach(rule.onNodeWithText(formatted(MISHEARD)))
        reader.assertShowing(MISHEARD)
    }

    @Test
    fun aCodeWithItsCheckCharacterOpens() {
        launch()
        reader.search("#G3RJM-8X3L1*L")
        reader.assertShowing("G3RJM8X3L1")
    }

    @Test
    fun aCodeWhoseCheckDisagreesSaysSo() {
        launch()
        // Looked up as a name, this found nothing and said nothing about the check.
        reader.search("#G3RJM-8X3L1*Q")
        reader.waitForText(reader.text(R.string.problem_check))
        reader.audit()
    }

    @Test
    fun coordinatesOpenAsTheirCode() {
        launch()
        reader.search("43.649061, -79.371679")
        reader.assertShowing("G3RJM8X3L1")
    }

    @Test
    fun aLinkToTheSiteOpensItsPlace() {
        launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://gridpointcode.com/play?c=G3RJM8X3L1")))
        reader.assertShowing("G3RJM8X3L1")
        rule.onNodeWithText(reader.text(R.string.source_link), ignoreCase = true).assertExists()
    }

    @Test
    fun aGeoUriFromAnotherAppOpensItsPlace() {
        launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:43.649061,-79.371679")))
        reader.assertShowing("G3RJM8X3L1")
    }

    @Test
    fun textSelectedInAnotherAppOpensItsPlace() {
        launch(Intent(Intent.ACTION_PROCESS_TEXT).putExtra(Intent.EXTRA_PROCESS_TEXT, "#G3RJM-8X3L1"))
        reader.assertShowing("G3RJM8X3L1")
    }

    @Test
    fun theLauncherShortcutOpensTheEmergencyCard() {
        reader.fix(43.645458, -79.380628)
        launch(Intent(ACTION_EMERGENCY))
        reader.waitFor { rule.onNodeWithText(reader.text(R.string.emergency_title)) }
        // The plain latitude and longitude first, which anyone can use.
        reader.waitForText("43.645458, -79.380628", millis = 20_000)
        reader.audit()
    }

    private companion object {
        /** The example the specification opens with, which a fresh install shows. */
        const val SAMPLE = "G3RJM98NM9"
        const val KENSINGTON = "G3RJL5FRCR"
        const val MARKET = "G3RJM8X3L1"
        const val MISHEARD = "G3RJM9X3L1"
    }
}
