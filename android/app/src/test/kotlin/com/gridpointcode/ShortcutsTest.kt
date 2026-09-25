package com.gridpointcode

import android.view.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What each key means at a desk, and what it leaves to a text field. */
class ShortcutsTest {

    private fun key(code: Int, ctrl: Boolean = false, shift: Boolean = false, typing: Boolean = false) =
        shortcutFor(code, ctrl = ctrl, alt = false, shift = shift, meta = false, typing = typing)

    @Test
    fun theArrowsNudgeThePlaceAsThePadDoes() {
        assertEquals(Shortcut.NUDGE_NORTH, key(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(Shortcut.NUDGE_EAST, key(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(Shortcut.NUDGE_SOUTH, key(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(Shortcut.NUDGE_WEST, key(KeyEvent.KEYCODE_DPAD_LEFT))
    }

    @Test
    fun ctrlKSearchesFromAnywhereAndCtrlCCopiesTheCode() {
        assertEquals(Shortcut.SEARCH, key(KeyEvent.KEYCODE_K, ctrl = true))
        assertEquals(Shortcut.SEARCH, key(KeyEvent.KEYCODE_K, ctrl = true, typing = true))
        assertEquals(Shortcut.COPY, key(KeyEvent.KEYCODE_C, ctrl = true))
    }

    @Test
    fun aTextFieldKeepsItsArrowsAndItsCopy() {
        assertNull(key(KeyEvent.KEYCODE_DPAD_LEFT, typing = true))
        assertNull(key(KeyEvent.KEYCODE_DPAD_UP, typing = true))
        assertNull(key(KeyEvent.KEYCODE_C, ctrl = true, typing = true))
    }

    @Test
    fun aKeyWithAnotherModifierIsNotAShortcut() {
        assertNull(key(KeyEvent.KEYCODE_DPAD_UP, shift = true), "shift and an arrow is a selection, not a nudge")
        assertNull(key(KeyEvent.KEYCODE_C, ctrl = true, shift = true))
        assertNull(key(KeyEvent.KEYCODE_K), "a plain K is a letter")
        assertNull(shortcutFor(KeyEvent.KEYCODE_DPAD_UP, ctrl = false, alt = true, shift = false, meta = false, typing = false))
    }
}
