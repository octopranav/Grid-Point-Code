package com.gridpointcode.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Writes the baseline profile: the code a reader's first moments run, which the
 * release then carries, so that on install Android compiles it ahead of time
 * instead of interpreting it on first use. The journey is what most readers do
 * first: the app opens on a place, a code is typed, the panel is pulled up and
 * looked through, and Saved and Settings are visited.
 *
 * ./gradlew :app:generateBaselineProfile runs it on the managed emulator and
 * writes the profile into app/src/release/generated/baselineProfiles.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun aReadersFirstMoments() = rule.collect(
        packageName = APP,
        // Stable over two passes is enough for one journey; each pass is a
        // minute on an emulator drawing in software, which can fall over in a
        // long run of them.
        maxIterations = 6,
        stableIterations = 2,
        includeInStartupProfile = true,
    ) {
        readyTheDevice()
        pressHome()
        startActivityAndWait()
        waitForTheCard()
        goTo(MARKET)
        openThePanel()
        scrollThePanel()
        visitTheTabs()
    }
}
