package com.gridpointcode.wear

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The last fix the tile and the complication show: when it is replaced, and for how long it is shown. */
class LastFixTest {

    private val first = LastFix("G3RJM98NM9", 12, 1_000_000L)

    @Test
    fun aNewCodeOrATighterFixReplacesItAtOnce() {
        assertTrue(null.replacedBy(first), "nothing kept yet")
        assertTrue(first.replacedBy(first.copy(code = "G3RJM98NMC", at = first.at + 2_000)), "a new code")
        assertTrue(first.replacedBy(first.copy(metres = 5, at = first.at + 2_000)), "a tighter fix")
    }

    @Test
    fun theSameFixIsWrittenAgainOnlyOnceAMinute() {
        assertFalse(first.replacedBy(first.copy(at = first.at + 2_000)), "every two seconds would redraw the tile every two seconds")
        assertFalse(first.replacedBy(first.copy(metres = 30, at = first.at + 2_000)), "a looser fix of the same code waits")
        assertTrue(first.replacedBy(first.copy(at = first.at + LastFix.REFRESH_MS)))
    }

    @Test
    fun itIsShownForAnHourAndNotBeforeItWasFound() {
        assertTrue(first.shown(first.at))
        assertTrue(first.shown(first.at + LastFix.SHOWN_FOR_MS - 1))
        assertFalse(first.shown(first.at + LastFix.SHOWN_FOR_MS), "an old code reads as where the wrist is now")
        assertFalse(first.shown(first.at - 1), "a clock set back does not bring it back")
    }
}
