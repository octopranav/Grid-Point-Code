package com.gridpointcode

import android.content.Intent
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A reader who says no to location. The question is the system's own dialog,
 * outside the app, so UiAutomator answers it; the app must then say why it
 * cannot show where they are and how to change their mind, not hang.
 */
@RunWith(AndroidJUnit4::class)
class LocationRefusedTest {

    @get:Rule
    val rule = accessibleRule()

    @get:Rule
    val record = RecordOnFailure()

    private val reader = Reader(rule)

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    /**
     * A question left unanswered outlives the app: clearing its data for the
     * next test does not close the system's dialog, and the dialog keeps the
     * focus from every test after it.
     */
    @After
    fun leaveNoQuestionOpen() {
        if (device.hasObject(By.pkg(PERMISSIONS))) device.pressBack()
    }

    @Test
    fun refusingLocationOnTheEmergencyCardSaysSoAndOffersAWayBack() {
        ActivityScenario.launch<MainActivity>(Intent(ACTION_EMERGENCY).setClass(reader.context, MainActivity::class.java))
        check(device.wait(Until.hasObject(By.pkg(PERMISSIONS)), 15_000)) { "The system never asked for location." }
        val deny = device.wait(Until.findObject(By.res(DENY_ID)), 5_000) ?: device.wait(Until.findObject(By.text(DENY_WORDS)), 5_000)
        checkNotNull(deny) { "The system asked, but offered no way to say no." }.click()
        reader.waitFor(15_000) { rule.onNodeWithText(reader.text(R.string.emergency_refused)) }
        rule.onNodeWithText(reader.text(R.string.emergency_allow)).assertExists()
        reader.audit()
    }

    private companion object {
        /**
         * The system's question comes from the platform's permission
         * controller on some system images and Google's on others, so it is
         * found by its name's ending, and its refusal by the button's own
         * name, or failing that, by what the button says.
         */
        val PERMISSIONS: Pattern = Pattern.compile(".*permissioncontroller")
        val DENY_ID: Pattern = Pattern.compile(".*:id/permission_deny_button")
        val DENY_WORDS: Pattern = Pattern.compile("Don.t allow|Deny", Pattern.CASE_INSENSITIVE)
    }
}
