package com.gridpointcode.core

import java.util.Locale

/*
 * The emergency card: where the reader is, in forms anybody can use.
 *
 * Built for being read to somebody who has never heard of this format, so the
 * plain coordinates stand beside the code rather than behind it. Everything on
 * it is worked out on the device, with no connection. It shows only a fix from
 * the device itself, never a place the reader picked or typed: a card that
 * says "where I am" must not be quietly describing somewhere else.
 */

/** What the emergency card can say. */
sealed interface Emergency {
    /** Listening, and no fix has arrived yet. */
    data object Finding : Emergency

    /**
     * Where the device says it is.
     *
     * @property decimal latitude and longitude to six places, always with a full stop,
     *   because a decimal comma between two numbers already separated by one is a trap
     * @property refining still listening, and the fix may yet get tighter
     */
    data class Here(
        val formatted: String,
        val spoken: String,
        /** Everything read out when the button is pressed, in the listener's language. */
        val said: String,
        val decimal: String,
        val degrees: String,
        val metres: Int,
        val insideOneCell: Boolean,
        val refining: Boolean,
    ) : Emergency

    data object Refused : Emergency

    data object Off : Emergency

    data object NoFix : Emergency
}

/** The card for what the screen shows now. */
fun emergencyOf(view: PlaceView): Emergency {
    when (view.problem) {
        Problem.LocationRefused -> return Emergency.Refused
        Problem.LocationOff -> return Emergency.Off
        Problem.NoFix -> return Emergency.NoFix
        else -> Unit
    }
    // A fix from before the button was pressed is not where the reader is now.
    if (view.locating == Locating.SEEKING) return Emergency.Finding
    val fix = view.fix ?: return Emergency.Finding
    val point = view.selection.point
    val decimal = String.format(Locale.ROOT, "%.6f, %.6f", point.latitude, point.longitude)
    return Emergency.Here(
        formatted = view.formatted,
        spoken = view.spoken,
        said = String.format(Locale.ROOT, view.listener.emergency, aloudCoordinates(decimal, view.listener), view.spoken),
        decimal = decimal,
        degrees = view.forms.first { it.key == FormKey.DMS }.value,
        metres = fix.metres,
        insideOneCell = fix.insideOneCell,
        refining = view.locating == Locating.REFINING,
    )
}
