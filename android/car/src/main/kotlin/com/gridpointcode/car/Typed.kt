package com.gridpointcode.car

import com.gridpointcode.core.OtherFormat
import com.gridpointcode.core.PlaceState
import com.gridpointcode.core.Point
import com.gridpointcode.core.Problem
import com.gridpointcode.core.Reading
import com.gridpointcode.core.Selection
import com.gridpointcode.core.Source
import com.gridpointcode.core.opened
import com.gridpointcode.core.read
import com.gridpointcode.core.selectionAt

/**
 * What was typed into the car's box, read by the phone's own rules: the same
 * reader, the same library, so a code opens in the car where it opens on the
 * phone. The car only adds what it cannot do.
 */
sealed interface Typed {
    /** A place to show, with the directions a link carried. */
    data class Place(val selection: Selection, val note: String) : Typed

    /** A short form, read against where the car is, before the car has been found. */
    data object NeedsFix : Typed

    /** The first characters of a code: an area, not one place to drive to. */
    data object Area : Typed

    /** A short form with a place name, which needs the name index the phone reads. */
    data object Anchored : Typed

    /** Anything the phone would refuse too, with its reason. */
    data class Refused(val problem: Problem?) : Typed
}

/**
 * Reads [text] against [here], where the car is, if it has been found. A short
 * form is never read against anywhere else: read against somewhere the car is
 * not, it names a plausible door kilometres away.
 */
fun typed(text: String, here: Point?): Typed {
    val reading = read(text)
    val needsHere = reading is Reading.Short ||
        (reading is Reading.Other && reading.found.let { it is OtherFormat.Near && it.locality == null })
    if (here == null && needsHere) return Typed.NeedsFix
    if (reading is Reading.Anchored) return Typed.Anchored
    val start = PlaceState(selection = if (here != null) selectionAt(here, Source.DEVICE) else selectionAt(NOWHERE, Source.SAMPLE))
    val opened = start.opened(text, Source.CODE)
    return when {
        opened.problem != null -> Typed.Refused(opened.problem)
        opened.area != null -> Typed.Area
        else -> Typed.Place(opened.selection, opened.note)
    }
}

/** A starting place no reading here is read against: only short forms are, and they wait for a fix. */
private val NOWHERE = Point(0.0, 0.0)
