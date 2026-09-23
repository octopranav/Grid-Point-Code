package com.gridpointcode.core

import ca.pranavpatel.algo.gridpointcode.GPC
import java.util.Locale
import kotlin.math.roundToInt

/** Why a piece of text, or a press of the locate button, did not become a place. */
sealed interface Problem {
    /** Nothing the reader understands, with the library's reason when it gave one. */
    data class Unread(val reason: String?) : Problem

    /** A well-formed code from the reserved range. Not wrong, and not a place. */
    data class Reserved(val code: String) : Problem

    /** Something shaped like a short form whose symbols the library refused. */
    data class UnreadShort(val short: String) : Problem

    /** The reader declined to let the app use the device's location. */
    data object LocationRefused : Problem

    /** Location is switched off for the whole device. */
    data object LocationOff : Problem

    /** The app listened for as long as it would wait, and the device never gave a fix. */
    data object NoFix : Problem
}

/** Whether the app is listening to the device's location. */
enum class Locating {
    IDLE,

    /** The button was pressed and no fix has arrived yet. */
    SEEKING,

    /** A fix has arrived, and the app keeps listening for a tighter one. */
    REFINING,
}

/**
 * The whole state of the place screen, and the only thing that changes.
 *
 * Every way a place can arrive goes through one of the functions below, and
 * each returns a new state rather than editing a surface. The rules about what
 * survives a move live here, where they can be tested, because the website's
 * panel broke five times in the same way: something written for one place that
 * stayed on screen after the reader had gone to another.
 *
 * @property note directions written for this place, carried in its link
 */
data class PlaceState(
    val selection: Selection,
    val note: String = "",
    val problem: Problem? = null,
    val locating: Locating = Locating.IDLE,
)

/**
 * A different place. Directions and any complaint belong to the place they were
 * written about, so both go, and so does any listening for the device: the
 * reader has chosen somewhere, and a late fix must not take it away from them.
 */
fun PlaceState.placed(next: Selection, note: String = ""): PlaceState =
    PlaceState(selection = next, note = tidyNote(note))

/**
 * One cell over. This refines the same place rather than leaving it: the reader
 * is lining the code up with a door, so the directions to that door stay. It
 * also ends any listening, because a hand on the pad knows better than the fix.
 */
fun PlaceState.nudged(direction: Compass): PlaceState {
    val code = around(selection.code)[direction] ?: return this
    return copy(selection = selectionOf(code, Source.NUDGE), problem = null, locating = Locating.IDLE)
}

/** Whatever was typed, pasted, linked or selected, read and gone to if it is a place. */
fun PlaceState.opened(text: String, source: Source): PlaceState =
    when (val reading = read(text)) {
        is Reading.Code -> placed(selectionOf(reading.code, source), reading.note)
        is Reading.At -> placed(selectionAt(reading.point, source))
        is Reading.Short -> recover(reading.text, selection.point)
            ?.let { placed(it) }
            ?: copy(problem = Problem.UnreadShort(reading.text))
        is Reading.Reserved -> copy(problem = Problem.Reserved(reading.code))
        is Reading.Unread -> copy(problem = Problem.Unread(reading.reason))
    }

/** New directions for the place already selected. */
fun PlaceState.described(text: String): PlaceState = copy(note = tidyNote(text))

/** The locate button was pressed. */
fun PlaceState.locating(): PlaceState = copy(locating = Locating.SEEKING, problem = null)

/**
 * A fix from the device.
 *
 * The first one after the button is pressed is a new place. Every later one is
 * the same place measured again, so it replaces the point only when it claims to
 * be tighter, and directions typed in the meantime stay. A looser fix is never
 * allowed to undo a better one.
 *
 * Listening ends once a fix is inside a single cell, which is as fine as a code
 * can use. Nothing here averages fixes together: close-together fixes from one
 * device share their errors, so an average would claim an accuracy it has not
 * got. The best fix the device gave is kept, with the device's own estimate.
 */
fun PlaceState.located(point: Point, accuracyMetres: Double): PlaceState {
    if (locating == Locating.IDLE) return this
    if (!GPC.IsValid(point.latitude, point.longitude).IsValid) return this

    val current = selection.accuracyMetres
    if (locating == Locating.REFINING && current != null && accuracyMetres >= current) return this

    val fix = selectionAt(point, Source.DEVICE, accuracyMetres)
    val next = if (locating == Locating.REFINING) copy(selection = fix, problem = null) else placed(fix)
    val settled = accuracyMetres.roundToInt() <= SINGLE_CELL_METRES
    return next.copy(locating = if (settled) Locating.IDLE else Locating.REFINING)
}

/**
 * The app stopped listening. With a fix, the best one stays, its accuracy shown.
 * With none, the reader is told, rather than left looking at a button that seems
 * to have done nothing.
 */
fun PlaceState.stoppedLocating(): PlaceState = when (locating) {
    Locating.SEEKING -> copy(locating = Locating.IDLE, problem = Problem.NoFix)
    else -> copy(locating = Locating.IDLE)
}

fun PlaceState.locationRefused(): PlaceState = copy(locating = Locating.IDLE, problem = Problem.LocationRefused)

fun PlaceState.locationOff(): PlaceState = copy(locating = Locating.IDLE, problem = Problem.LocationOff)

/** Everything the screen shows, derived in one pass so no part of it can be from a place before. */
data class PlaceView(
    val selection: Selection,
    val formatted: String,
    val spoken: String,
    val forms: List<Form>,
    val pad: Map<Compass, String>,
    val cell: CellSize,
    val fix: Fix?,
    val note: String,
    val link: String,
    val problem: Problem?,
    val locating: Locating,
)

fun PlaceState.view(locale: Locale = Locale.getDefault()): PlaceView = PlaceView(
    selection = selection,
    formatted = formatted(selection.code),
    spoken = aloud(selection.code),
    forms = forms(selection, locale),
    pad = around(selection.code),
    cell = cellSize(selection.point.latitude),
    fix = fixOf(selection),
    note = note,
    link = addressOf(selection.code, note),
    problem = problem,
    locating = locating,
)
