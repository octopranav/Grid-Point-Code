package com.gridpointcode.core

import java.util.Locale

/** Why a piece of text did not become a place. */
sealed interface Problem {
    /** Nothing the reader understands, with the library's reason when it gave one. */
    data class Unread(val reason: String?) : Problem

    /** A well-formed code from the reserved range. Not wrong, and not a place. */
    data class Reserved(val code: String) : Problem

    /** Something shaped like a short form whose symbols the library refused. */
    data class UnreadShort(val short: String) : Problem
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
)

/**
 * A different place. Directions and any complaint belong to the place they were
 * written about, so both go.
 */
fun PlaceState.placed(next: Selection, note: String = ""): PlaceState =
    PlaceState(selection = next, note = tidyNote(note))

/**
 * One cell over. This refines the same place rather than leaving it: the reader
 * is lining the code up with a door, so the directions to that door stay.
 */
fun PlaceState.nudged(direction: Compass): PlaceState {
    val code = around(selection.code)[direction] ?: return this
    return copy(selection = selectionOf(code, Source.NUDGE), problem = null)
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
)
