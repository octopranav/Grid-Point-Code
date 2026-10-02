package com.gridpointcode

import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ComposeAccessibilityValidator
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult.AccessibilityCheckResultType
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityValidator
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityViewCheckResult
import java.util.Locale
import leakcanary.DetectLeaksAfterTestSuccess
import org.junit.rules.RuleChain
import org.junit.rules.TestRule

/**
 * A Compose test rule that runs Google's Accessibility Test Framework over the
 * whole screen after every action the test takes, and fails the test on any
 * error it finds: a control too small to touch, text too faint to read, an
 * image with nothing for a screen reader to say. The app launches itself in
 * each test, so the rule launches nothing. It readies the device first, and
 * after the test fails it on anything the app leaked or any StrictMode
 * violation of the app's own.
 */
fun accessibleRule(): AndroidComposeTestRule<TestRule, ComponentActivity> =
    AndroidComposeTestRule<TestRule, ComponentActivity>(
        activityRule = RuleChain.outerRule(ReadyDevice)
            .around(DetectLeaksAfterTestSuccess())
            .around(NoStrictModeViolations),
        activityProvider = { error("Each test launches the app itself.") },
    ).apply {
        val validator = AccessibilityValidator()
            .setRunChecksFromRootView(true)
            .setThrowExceptionFor(null)
        setComposeAccessibilityValidator(
            object : ComposeAccessibilityValidator {
                override fun check(view: View) {
                    val errors = validator.checkAndReturnResults(view)
                        .filter { it.type == AccessibilityCheckResultType.ERROR }
                        .filterNot(::isComposeWindowRoot)
                    if (errors.isNotEmpty()) {
                        throw AssertionError(
                            errors.joinToString("\n", prefix = "Accessibility errors:\n") { error ->
                                "${error.getMessage(Locale.ENGLISH)} on ${describe(error.view)}"
                            },
                        )
                    }
                }
            },
        )
    }

/**
 * The one result set aside: the framework reporting the root view of a Compose
 * window, the activity's own, the emergency card's dialog or a text field's
 * cursor handle, as focusable with nothing to say. The check counts only real
 * views as a view's children, not the semantics Compose draws inside it, so it
 * passes the root on a screen that happens to hold a real view, the map, and
 * fails it on one that holds none. A screen reader moves through those
 * semantics and never stops on the root, and every one of them is still checked,
 * so only that root, and only for that check, is passed over.
 */
private fun isComposeWindowRoot(result: AccessibilityViewCheckResult): Boolean {
    val view = result.view ?: return false
    return result.sourceCheckClass.simpleName == "SpeakableTextPresentCheck" &&
        view.javaClass.name == "androidx.compose.ui.platform.AndroidComposeView"
}

/** A view as far as a reader of the failure needs it: what it is, where, and what holds it. */
private fun describe(view: View?): String {
    if (view == null) return "a view no longer on the screen"
    val at = IntArray(2).also(view::getLocationOnScreen)
    val parents = generateSequence(view.parent as? View) { it.parent as? View }
        .take(4).joinToString(" in ") { it.javaClass.simpleName }
    return "${view.javaClass.name} at (${at[0]}, ${at[1]}) ${view.width}x${view.height}" +
        ", described as \"${view.contentDescription ?: ""}\", in $parents"
}
