package com.gridpointcode.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * How long the app takes to show a place from cold, with the baseline profile
 * and without it, and how smoothly the panel scrolls. Each runs the release
 * build, as a reader gets it.
 *
 * Numbers worth keeping come from a real phone; an emulator measures its own
 * host as much as the app. On one, pass
 * -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartWithoutTheProfile() = coldStart(CompilationMode.None())

    @Test
    fun coldStartWithTheProfile() = coldStart(CompilationMode.Partial(BaselineProfileMode.Require))

    @Test
    fun scrollingThePanel() = rule.measureRepeated(
        packageName = APP,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        iterations = ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            readyTheDevice()
            pressHome()
            startActivityAndWait()
            waitForTheCard()
            openThePanel()
        },
    ) {
        scrollThePanel()
    }

    private fun coldStart(mode: CompilationMode) = rule.measureRepeated(
        packageName = APP,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        iterations = ITERATIONS,
        startupMode = StartupMode.COLD,
        setupBlock = {
            readyTheDevice()
            pressHome()
        },
    ) {
        startActivityAndWait()
        waitForTheCard()
    }

    private companion object {
        const val ITERATIONS = 10
    }
}
