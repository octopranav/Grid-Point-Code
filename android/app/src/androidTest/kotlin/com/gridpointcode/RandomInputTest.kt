package com.gridpointcode

import android.content.Intent
import android.util.Log
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import kotlin.random.Random
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Input nobody planned: from a fixed seed, taps on whatever the app shows, text
 * no reader would type, swipes on the map, Back, and the phone turned. Any crash
 * ends the run and fails it, and the accessibility checks run on every tap, in
 * states no other test reaches. Each action is logged under RandomInput, so a
 * failure can be replayed from its seed and its log.
 *
 * Kept inside the app, which is why it is not the platform's monkey: that needs
 * an emulator of its own, outside the one Gradle makes for the device tests.
 * What would only fetch from the network for minutes is left alone, keeping an
 * area or a country's names, and so are links, which open the browser.
 */
@RunWith(AndroidJUnit4::class)
class RandomInputTest {

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

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @After
    fun putTheDeviceBack() {
        device.setOrientationNatural()
        device.unfreezeRotation()
    }

    @Test
    fun seedOne() = wander(seed = 1)

    @Test
    fun seedTwo() = wander(seed = 2)

    @Test
    fun seedThree() = wander(seed = 3)

    private fun wander(seed: Int) {
        ActivityScenario.launch(MainActivity::class.java)
        val random = Random(seed)
        val done = mutableListOf<String>()
        try {
            repeat(ACTIONS) { step ->
                // An action can open another app a moment after it returns, a
                // link in the map's credit opening the browser, so the app is
                // brought back before each action, not only after.
                comeBackIfGone()
                val action = act(random)
                done += action
                Log.i(TAG, "seed $seed, step $step: $action")
            }
            comeBackIfGone()
        } catch (failure: Throwable) {
            throw AssertionError("Seed $seed failed after ${done.size} actions, the last: ${done.takeLast(LAST).joinToString(" | ")}", failure)
        }
        // Still answering, and still the app.
        rule.waitForIdle()
        reader.audit()
    }

    /** One action, chosen by [random], described for the log. */
    private fun act(random: Random): String = try {
        choose(random)
    } catch (gone: IllegalStateException) {
        // Another app came to the front between the check and the action.
        if (gone.message.orEmpty().startsWith("No compose hierarchies")) "the app was behind another" else throw gone
    }

    private fun choose(random: Random): String = when (random.nextInt(100)) {
        in 0..59 -> tapSomething(random)
        in 60..74 -> type(random)
        in 75..86 -> swipe(random)
        in 87..95 -> {
            device.pressBack()
            "back"
        }
        else -> {
            if (random.nextBoolean()) device.setOrientationLandscape() else device.setOrientationNatural()
            "turned"
        }
    }

    private fun tapSomething(random: Random): String {
        val tappable = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
        val nodes = tappable.fetchSemanticsNodes()
        val choices = nodes.indices.filter { i ->
            val node = nodes[i]
            val words = (node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()).joinToString(" ")
            // A control with no name of its own is a link inside a line of text,
            // the map's credit, which opens the browser and tests only that.
            words.isNotBlank() && node.boundsInRoot.width > 0 && node.boundsInRoot.height > 0 && LEFT_ALONE.none { words.contains(it) }
        }
        if (choices.isEmpty()) return "nothing to tap"
        val index = choices[random.nextInt(choices.size)]
        val node = nodes[index]
        val words = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
            ?: node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
            ?: "an unnamed control"
        tolerateGone { tappable[index].performClick() }
        return "tap $words"
    }

    private fun type(random: Random): String {
        val text = TEXTS[random.nextInt(TEXTS.size)]
        val field = rule.onAllNodes(hasSetTextAction() and hasText(reader.text(R.string.search_label), substring = true))
        if (field.fetchSemanticsNodes().isEmpty()) return "no field to type in"
        tolerateGone {
            field.onFirst().performTextReplacement(text)
            if (random.nextBoolean()) field.onFirst().performImeAction()
        }
        return "type \"$text\""
    }

    private fun swipe(random: Random): String {
        val from = Offset(random.nextFloat() * 800f + 100f, random.nextFloat() * 800f + 300f)
        val to = Offset(random.nextFloat() * 800f + 100f, random.nextFloat() * 800f + 300f)
        tolerateGone { rule.onAllNodes(isRoot()).onFirst().performTouchInput { swipe(from, to, durationMillis = 200) } }
        return "swipe"
    }

    /**
     * Back in the app if an action left it: Back out of the share sheet or the
     * system's settings, and if that is not enough, the app opened again.
     */
    private fun comeBackIfGone() {
        val app = reader.context.packageName
        repeat(2) {
            if (device.currentPackageName == app) return
            device.pressBack()
            device.waitForIdle()
        }
        if (device.currentPackageName != app) {
            val launch = checkNotNull(reader.context.packageManager.getLaunchIntentForPackage(app))
            reader.context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            device.waitForIdle()
        }
        rule.waitForIdle()
    }

    /**
     * Runs [action] on a node that may have gone in the moment since it was
     * chosen, or with the app gone behind another, neither of which is the app's
     * failure. Nothing else is forgiven: an exception the app throws from a tap
     * arrives here too, and once arrived as the same kind as the app being behind
     * another, and was swallowed until a deliberately broken Copy showed it.
     */
    private fun tolerateGone(action: () -> Unit) {
        try {
            action()
        } catch (gone: AssertionError) {
            if (gone.message.orEmpty().startsWith("Accessibility errors")) throw gone
        } catch (behind: IllegalStateException) {
            if (!behind.message.orEmpty().startsWith("No compose hierarchies")) throw behind
        }
    }

    private companion object {
        const val TAG = "RandomInput"
        const val ACTIONS = 120
        const val LAST = 8

        /** Controls that would only wait on a download: keeping an area, and a country's names. */
        val LEFT_ALONE = listOf("Keep this area offline", "Keep a country's names")

        /** What gets typed: codes, near codes, coordinates, links, names, and things no field expects. */
        val TEXTS = listOf(
            "G3RJM8X3L1", "#G3RJM-8X3L1*L", "g3rjm 8x3l1", "#G3RJM-8X3L1*Q", "G3RJM8X3L", "G3RJM8X3L12",
            "-8X3L1", "-8X3L1 St. Lawrence, Toronto", "43.6454, -79.3806", "geo:43.6454,-79.3806",
            "https://gridpointcode.com/play?c=G3RJM8X3L1&n=North+door", "87M2MJ5C+2M", "toronto", "QQQQQYYYYY",
            "", " ", "‮right to left", "📍📍", "0".repeat(200), "'; DROP TABLE places;--",
        )
    }
}
