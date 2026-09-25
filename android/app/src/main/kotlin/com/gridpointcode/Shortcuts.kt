package com.gridpointcode

import android.view.KeyEvent
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged

/*
 * The keyboard, for a reader at a desk: Android on a desktop, a tablet with a
 * keyboard cover, a phone with one plugged in. The keys are the ones a map on a
 * desk already answers to, and each does what a finger does on the screen.
 */

/** What a key press asks for. */
enum class Shortcut { SEARCH, COPY, NUDGE_NORTH, NUDGE_EAST, NUDGE_SOUTH, NUDGE_WEST }

/**
 * The shortcut a key press means, or null when it means none.
 *
 * Ctrl+K searches from anywhere, even from a text field, because it is how a
 * reader gets back to the search box. The rest wait while a text field has the
 * keyboard: there an arrow moves the cursor and Ctrl+C copies the selection.
 * Otherwise an arrow nudges the place one cell, as the pad does, and Ctrl+C
 * copies its code.
 */
fun shortcutFor(keyCode: Int, ctrl: Boolean, alt: Boolean, shift: Boolean, meta: Boolean, typing: Boolean): Shortcut? {
    val ctrlOnly = ctrl && !alt && !shift && !meta
    val plain = !ctrl && !alt && !shift && !meta
    return when {
        ctrlOnly && keyCode == KeyEvent.KEYCODE_K -> Shortcut.SEARCH
        typing -> null
        ctrlOnly && keyCode == KeyEvent.KEYCODE_C -> Shortcut.COPY
        !plain -> null
        keyCode == KeyEvent.KEYCODE_DPAD_UP -> Shortcut.NUDGE_NORTH
        keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> Shortcut.NUDGE_EAST
        keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> Shortcut.NUDGE_SOUTH
        keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> Shortcut.NUDGE_WEST
        else -> null
    }
}

/**
 * Which text fields have the keyboard. A field says so when its focus changes,
 * and says it no longer has it when it leaves the screen, so a field removed
 * while focused does not leave the arrows dead.
 */
class Typing {
    private val focused = mutableStateMapOf<String, Boolean>()

    /** Whether any field has the keyboard now. */
    val active: Boolean get() = focused.values.any { it }

    fun mark(field: String, hasKeyboard: Boolean) {
        focused[field] = hasKeyboard
    }
}

/** The screen's one record of typing, for the fields to report to. */
val LocalTyping = staticCompositionLocalOf { Typing() }

/** Reports this field's focus to [typing] under [field]. */
fun Modifier.typing(typing: Typing, field: String): Modifier = onFocusChanged { typing.mark(field, it.isFocused) }
