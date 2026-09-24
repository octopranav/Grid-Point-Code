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

    /** A location in a system whose owner alone can turn it into a place. */
    data class Closed(val format: Format) : Problem

    /** A short link, which says where it goes only when it is opened. */
    data class Unfollowed(val format: Format) : Problem

    /** A short code of another system whose town could not be found, or looked up. */
    data class Unplaced(val code: String, val locality: String, val why: Unanchored.Why) : Problem

    /** A short form given with a place that could not be used as its reference. */
    data class Unanchored(val short: String, val reference: String, val why: Why) : Problem {
        enum class Why {
            /** Nothing in the index is called that. */
            NOT_FOUND,

            /** More than one place answers to what was written, so a region would settle it. */
            SEVERAL,

            /**
             * The place shares its name with another in its own region, so the
             * archive left it out. The short form could be read against the
             * wrong one, and no region can settle it.
             */
            NOT_UNIQUE,

            /** The index or the archive could not be reached. */
            UNREACHABLE,
        }
    }

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
    val origin: Origin? = null,
    val area: AreaCell? = null,
)

/**
 * Where a converted place came from: the system, the text as it was given, and
 * how precise that source was. A code names 2.5 m wherever it came from, and
 * the reader is owed the difference when the source said less.
 */
data class Origin(val format: Format, val text: String, val metres: Double, val viewCentre: Boolean)

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
    return copy(selection = selectionOf(code, Source.NUDGE), problem = null, locating = Locating.IDLE, origin = null, area = null)
}

/** Whatever was typed, pasted, linked or selected, read and gone to if it is a place. */
fun PlaceState.opened(text: String, source: Source): PlaceState =
    when (val reading = read(text)) {
        is Reading.Code -> placed(selectionOf(reading.code, source), reading.note)
        is Reading.At -> placed(selectionAt(reading.point, source))
        is Reading.Short -> recover(reading.text, selection.point)
            ?.let { placed(it) }
            ?: copy(problem = Problem.UnreadShort(reading.text))
        // The place has to be looked up first, which is the caller's to do
        // (see [anchoredAt]). Recovering against wherever the screen happens to
        // be would name somewhere plausible and wrong.
        is Reading.Anchored -> unanchored(reading, Problem.Unanchored.Why.UNREACHABLE)
        is Reading.Area -> placed(selectionAt(reading.area.centre, Source.AREA)).copy(area = reading.area)
        is Reading.Other -> when (val other = reading.found) {
            is OtherFormat.At -> converted(other, text)
            // Another system's short code with no town is read against the
            // reader's own place, as a bare short form of this system is. With
            // a town, the town has to be looked up first, which is the caller's to do.
            is OtherFormat.Near -> when (val town = other.locality) {
                null -> recoverNear(other.code, selection.point)?.let { converted(it, text) }
                    ?: copy(problem = Problem.Unread(null))
                else -> copy(problem = Problem.Unplaced(other.code, town, Problem.Unanchored.Why.UNREACHABLE))
            }
            is OtherFormat.Closed -> copy(problem = Problem.Closed(other.format))
            is OtherFormat.Unfollowed -> copy(problem = Problem.Unfollowed(other.format))
        }
        is Reading.Reserved -> copy(problem = Problem.Reserved(reading.code))
        is Reading.Unread -> copy(problem = Problem.Unread(reading.reason))
    }

/**
 * A place chosen by name. Somewhere else by name is a different door, so it is a
 * new place like any other, and the directions written for the last one go.
 */
fun PlaceState.found(place: Named): PlaceState =
    runCatching { selectionOf(place.code, Source.SEARCH) }
        .map { placed(it) }
        .getOrDefault(copy(problem = Problem.Unread(null)))

/**
 * A short form read against the landmark it was given with, at the archive's
 * coordinates for it. A new place like any other, so the directions go.
 */
fun PlaceState.anchoredAt(short: String, landmark: Landmark): PlaceState =
    recover(short, Point(landmark.latitude, landmark.longitude))
        ?.let { placed(it.copy(source = Source.ANCHORED)) }
        ?: copy(problem = Problem.UnreadShort(short))

/** A short form whose landmark could not be used. The place stays where it was. */
fun PlaceState.unanchored(reading: Reading.Anchored, why: Problem.Unanchored.Why): PlaceState =
    copy(problem = Problem.Unanchored(reading.short, reading.reference, why))

/**
 * A saved place, opened again. A new place like any other, except that its own
 * directions come back with it: they were written for exactly this door.
 */
fun PlaceState.recalled(saved: SavedPlace): PlaceState =
    runCatching { selectionOf(saved.code, Source.SAVED) }
        .map { placed(it, saved.note) }
        .getOrDefault(copy(problem = Problem.Unread(null)))

/** A location from another system, converted: a new place, which remembers where it came from. */
fun PlaceState.converted(at: OtherFormat.At, text: String): PlaceState =
    placed(selectionAt(at.point, Source.CONVERTED)).copy(origin = Origin(at.format, text.trim(), at.metres, at.viewCentre))

/** A short code whose town could not be used. The place stays where it was. */
fun PlaceState.unplaced(near: OtherFormat.Near, why: Problem.Unanchored.Why): PlaceState =
    copy(problem = Problem.Unplaced(near.code, near.locality.orEmpty(), why))

/**
 * The area of a level around the place: what gets shared instead of the door.
 * The place stays selected underneath, so going back is one step.
 */
fun PlaceState.widened(level: Int): PlaceState =
    areaHolding(selection.code, level)?.let { copy(area = it, problem = null) } ?: this

/** Back from an area to the place inside it. */
fun PlaceState.narrowed(): PlaceState = copy(area = null)

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
    val origin: Origin?,
    val area: AreaView?,
    /** The areas around the place, a region down to a building, for choosing one to share. */
    val areas: List<AreaView>,
)

/** An area as the screen shows it: the cell, how to say it, its size where it lies, and its link. */
data class AreaView(
    val cell: String,
    val level: Int,
    val size: CellSize,
    val spoken: String,
    val link: String,
    val box: Box,
)

private fun AreaCell.view(): AreaView =
    AreaView(cell, level, cellSize(centre.latitude, level), aloudArea(cell), areaAddress(cell), box)

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
    origin = origin,
    area = area?.view(),
    areas = AREA_LEVELS.reversed().mapNotNull { level -> areaHolding(selection.code, level)?.view() },
)
