package com.gridpointcode

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gridpointcode.core.Anchor
import com.gridpointcode.core.Compass
import com.gridpointcode.core.KeptMatch
import com.gridpointcode.core.Landmark
import com.gridpointcode.core.Locating
import com.gridpointcode.core.Named
import com.gridpointcode.core.PlaceState
import com.gridpointcode.core.PlaceView
import com.gridpointcode.core.Problem
import com.gridpointcode.core.Reading
import com.gridpointcode.core.ReferenceMatch
import com.gridpointcode.core.Point
import com.gridpointcode.core.Selection
import com.gridpointcode.core.Source
import com.gridpointcode.core.anchored
import com.gridpointcode.core.anchoredAt
import com.gridpointcode.core.areaMetres
import com.gridpointcode.core.keptReference
import com.gridpointcode.core.described
import com.gridpointcode.core.found
import com.gridpointcode.core.isName
import com.gridpointcode.core.located
import com.gridpointcode.core.locating
import com.gridpointcode.core.locationOff
import com.gridpointcode.core.locationRefused
import com.gridpointcode.core.nudged
import com.gridpointcode.core.opened
import com.gridpointcode.core.matchReference
import com.gridpointcode.core.placed
import com.gridpointcode.core.read
import com.gridpointcode.core.recover
import com.gridpointcode.core.referenceName
import com.gridpointcode.core.unanchored
import com.gridpointcode.core.selectionAt
import com.gridpointcode.core.stoppedLocating
import com.gridpointcode.core.view
import com.gridpointcode.map.Basemap
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Holds the place, and listens to the device when asked.
 *
 * What a fix may do to the place, and what survives a move, are decided in
 * `:core` where they are tested. This class keeps the state across a rotation,
 * hands the screen a view derived from it in one pass, and makes sure the
 * satellites are not left running once nobody is waiting for them.
 */
class PlaceViewModel(application: Application) : AndroidViewModel(application) {

    private val device = DeviceLocation(application)
    private val preferences = Preferences(application)
    private val chosen = MutableStateFlow(preferences.basemap())
    private val state = MutableStateFlow(PlaceState(selectionAt(SAMPLE, Source.SAMPLE)))
    private var listening: Job? = null
    private val names = NameIndex()
    private val finding = MutableStateFlow(Finding())
    private var looking: Job? = null
    private val archive = LandmarkArchive(KeptLandmarks(File(application.noBackupFilesDir, "kept-landmarks")))
    private val anchoring = MutableStateFlow(Anchoring())
    private val keeping = MutableStateFlow(Keeping())

    /** Which landmark the reader chose, by name and region, so it survives a nudge that keeps it in reach. */
    private var anchorChoice: String? = null

    val ui: StateFlow<PlaceView> = state
        .map { it.view() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, state.value.view())

    /** The basemap the reader chose, remembered across launches. */
    val basemap: StateFlow<Basemap> = chosen

    fun choose(next: Basemap) {
        chosen.value = next
        preferences.remember(next)
    }

    /**
     * What is in the search field. Kept here rather than in the field, so that
     * choosing a place can empty it, however the place was chosen.
     */
    var query by mutableStateOf("")
        private set

    /** What the search field has turned up by name. */
    val found: StateFlow<Finding> = finding

    /** The landmarks the current place's short form can be given with. */
    val anchors: StateFlow<Anchoring> = anchoring

    /** Whether the area around the current place is kept on the device. */
    val offline: StateFlow<Keeping> = keeping

    init {
        // Looked up again whenever the code changes, and only then: a finer fix
        // inside the same cell names the same code and needs the same list. A
        // lookup still running when the reader moves on is cancelled, so a slow
        // answer for the last place cannot land on this one.
        viewModelScope.launch {
            state.distinctUntilChangedBy { it.selection.code }.collectLatest { current ->
                val code = current.selection.code
                anchoring.value = Anchoring(code = code)
                anchoring.value = when (val near = archive.near(current.selection.point)) {
                    LandmarkArchive.Near.Unreachable -> Anchoring(code, Anchoring.Status.UNREACHABLE)
                    is LandmarkArchive.Near.Found -> reach(code, near.anchors).copy(partial = near.partial)
                }
            }
        }
        viewModelScope.launch {
            state.distinctUntilChangedBy { it.selection.code }.collectLatest { current ->
                val fresh = keepingAt(current.selection.point)
                keeping.update { fresh.copy(busy = it.busy) }
            }
        }
    }

    /** Keep the area around the current place, so its landmarks work with no connection. */
    fun keepArea() {
        val point = state.value.selection.point
        keeping.update { it.copy(busy = true, note = null) }
        viewModelScope.launch {
            val outcome = archive.keep(point)
            val fresh = keepingAt(state.value.selection.point)
            keeping.value = fresh.copy(note = if (outcome is LandmarkArchive.Keeping.Failed) Keeping.Note.FAILED else null)
        }
    }

    /** Give back every area kept. */
    fun forgetKept() {
        viewModelScope.launch {
            archive.forget()
            keeping.value = keepingAt(state.value.selection.point).copy(note = Keeping.Note.FORGOTTEN)
        }
    }

    private suspend fun keepingAt(point: Point): Keeping {
        val here = archive.area(point) ?: return Keeping()
        val all = archive.keptAreas()
        val (northSouth, eastWest) = areaMetres(point.latitude, here.first.length + 1)
        return Keeping(
            area = here.first,
            here = here.second,
            northSouthKm = (northSouth / 1000).roundToInt(),
            eastWestKm = (eastWest / 1000).roundToInt(),
            areas = all.size,
            bytes = all.sumOf { it.bytes },
        )
    }

    /** A place from the map. */
    fun place(next: Selection) {
        settle()
        change { it.placed(next) }
    }

    fun nudge(direction: Compass) = change { it.nudged(direction) }

    fun open(text: String, source: Source = Source.CODE) {
        val reading = read(text)
        if (reading is Reading.Anchored) return readAnchored(text, reading)
        settle()
        change { it.opened(text, source) }
    }

    /**
     * A short form given with a landmark: `-98NM9 near Old Toronto, Ontario,
     * Canada`. The place is found by name in the index, then in the archive for
     * the coordinates the sender's list was drawn from, and the five characters
     * are recovered against those. Anything short of one certain landmark is
     * refused with the reason, and the place stays where it was: a guess would
     * be recovered into somewhere plausible and wrong.
     */
    private fun readAnchored(text: String, reading: Reading.Anchored) {
        settle()
        // Symbols no code contains need nothing looked up to be refused.
        if (recover(reading.short, Point(0.0, 0.0)) == null) {
            return change { it.copy(problem = Problem.UnreadShort(reading.short)) }
        }
        finding.value = Finding(query = text, status = Finding.Status.LOOKING)
        looking = viewModelScope.launch {
            val outcome = resolve(reading)
            finding.value = Finding()
            when (outcome) {
                is Resolved.At -> {
                    // So the anchor list offers the same landmark first, and
                    // passing the place on uses the reference it arrived with.
                    anchorChoice = identity(outcome.landmark)
                    change { it.anchoredAt(reading.short, outcome.landmark) }
                }
                is Resolved.Refused -> change { it.unanchored(reading, outcome.why) }
            }
        }
    }

    private suspend fun resolve(reading: Reading.Anchored): Resolved {
        val places = names.find(referenceName(reading.reference), REFERENCE_ROWS)
            ?: return resolveKept(reading)
        val place = when (val match = matchReference(reading.reference, places)) {
            ReferenceMatch.None -> return Resolved.Refused(Problem.Unanchored.Why.NOT_FOUND)
            ReferenceMatch.Several -> return Resolved.Refused(Problem.Unanchored.Why.SEVERAL)
            is ReferenceMatch.One -> match.place
        }
        return when (val held = archive.landmark(place)) {
            LandmarkArchive.Held.Unreachable -> Resolved.Refused(Problem.Unanchored.Why.UNREACHABLE)
            LandmarkArchive.Held.Missing -> Resolved.Refused(Problem.Unanchored.Why.NOT_UNIQUE)
            is LandmarkArchive.Held.Found -> Resolved.At(held.landmark)
        }
    }

    /**
     * With no connection to the name index, the areas kept on the device are
     * searched instead. Found there, the landmark is the archive's own, so the
     * line reads exactly as it would online; not found there says nothing about
     * the rest of the world, so the reader is told a connection is needed.
     */
    private suspend fun resolveKept(reading: Reading.Anchored): Resolved =
        when (val match = keptReference(reading.reference, archive.keptLandmarks())) {
            is KeptMatch.One -> Resolved.At(match.landmark)
            KeptMatch.Several -> Resolved.Refused(Problem.Unanchored.Why.SEVERAL)
            KeptMatch.None -> Resolved.Refused(Problem.Unanchored.Why.UNREACHABLE)
        }

    private sealed interface Resolved {
        data class At(val landmark: Landmark) : Resolved
        data class Refused(val why: Problem.Unanchored.Why) : Resolved
    }

    /**
     * The search field changed. It is looked up by name only when it is nothing
     * else, and only once the typing pauses: a keystroke is not worth a request.
     * While a search is out, the places already listed stay listed, so the list
     * does not blink with every letter.
     */
    fun type(text: String) {
        query = text
        looking?.cancel()
        if (!isName(text)) {
            finding.value = Finding()
            return
        }
        looking = viewModelScope.launch {
            delay(TYPING_MS)
            finding.update { it.copy(query = text, status = Finding.Status.LOOKING) }
            finding.value = answer(text, names.find(text))
        }
    }

    /** The landmark the reader wants the short form given with. */
    fun anchorTo(anchor: Anchor) {
        anchorChoice = identity(anchor)
        anchoring.update { if (anchor in it.anchors) it.copy(chosen = anchor) else it }
    }

    /**
     * The list for a code, keeping the reader's choice while it is still within
     * reach. When a nudge carries it out of the box it is dropped rather than
     * left standing, and the nearest takes its place.
     */
    private fun reach(code: String, anchors: List<Anchor>): Anchoring {
        if (anchors.isEmpty()) return Anchoring(code, Anchoring.Status.NONE)
        val kept = anchors.firstOrNull { identity(it) == anchorChoice }
        return Anchoring(code, Anchoring.Status.FOUND, anchors, kept ?: anchors.first())
    }

    /** Two landmarks can share a name; within one region they cannot. */
    private fun identity(anchor: Anchor) = identity(anchor.landmark)

    private fun identity(landmark: Landmark) = landmark.name + "\n" + landmark.region

    /** A place chosen from the list. */
    fun pick(place: Named) {
        settle()
        query = ""
        change { it.found(place) }
    }

    /**
     * Go. A code, a point or a link opens as it always has. A name goes to the
     * first place listed for it, which for a whole name is the largest place
     * called that.
     */
    fun go(text: String) {
        if (!isName(text)) return open(text)
        looking?.cancel()
        looking = viewModelScope.launch {
            val places = names.find(text)
            val first = places?.firstOrNull()
            if (first == null) {
                finding.value = answer(text, places)
            } else {
                finding.value = Finding()
                query = ""
                change { it.found(first) }
            }
        }
    }

    fun describeTheWay(text: String) = state.update { it.described(text) }

    /** The reader said no to the permission prompt. */
    fun refused() = change { it.locationRefused() }

    /**
     * Listen until a fix is inside one cell, the reader goes somewhere else, or
     * patience runs out. The first fix arrives in a second or two with a warm
     * start, and can take much longer indoors; the screen says which is happening.
     */
    fun locate() {
        if (!device.permitted()) return change { it.locationRefused() }
        if (!device.enabled()) return change { it.locationOff() }

        listening?.cancel()
        state.update { it.locating() }
        listening = viewModelScope.launch {
            withTimeoutOrNull(PATIENCE_MS) {
                device.fixes().firstOrNull { fix ->
                    state.update { it.located(Point(fix.latitude, fix.longitude), fix.accuracy.toDouble()) }
                    state.value.locating == Locating.IDLE
                }
            }
            if (state.value.locating != Locating.IDLE) state.update { it.stoppedLocating() }
        }
    }

    /** A place has been chosen some other way, so nothing still being looked up may arrive after it. */
    private fun settle() {
        looking?.cancel()
        looking = null
        finding.value = Finding()
    }

    private fun answer(query: String, places: List<Named>?): Finding = when {
        places == null -> Finding(query, status = Finding.Status.OFFLINE)
        places.isEmpty() -> Finding(query, status = Finding.Status.MISSING)
        else -> Finding(query, places, Finding.Status.FOUND)
    }

    /** Every change that can end listening also stops the device, so nothing runs for nobody. */
    private fun change(transition: (PlaceState) -> PlaceState) {
        state.update(transition)
        if (state.value.locating == Locating.IDLE) {
            listening?.cancel()
            listening = null
        }
    }

    private companion object {
        /** The specification's own example, until the device says where it is. */
        val SAMPLE = Point(43.650006, -79.380004)

        /** Long enough for a cold start by a window; a fix inside one cell ends it sooner. */
        const val PATIENCE_MS = 30_000L

        /** The pause in typing that counts as having typed something, the same as the website's. */
        const val TYPING_MS = 180L

        /**
         * Enough index rows to hold every place of one name: a name would need
         * more regions than the index has to run past it. Deciding that a name
         * alone is unique needs all of them, not the first dozen.
         */
        const val REFERENCE_ROWS = 5_000
    }
}

/** The landmarks near enough to anchor the short form, and the one chosen. */
data class Anchoring(
    val code: String = "",
    val status: Status = Status.LOOKING,
    val anchors: List<Anchor> = emptyList(),
    val chosen: Anchor? = null,
    /** Part of the box could not be read, so the list is missing places, and says so. */
    val partial: Boolean = false,
) {
    /** The line to share: the short form, then the place, as section 12.1 writes it. */
    val line: String? get() = chosen?.let { anchored(code, it.landmark) }

    enum class Status {
        LOOKING,
        FOUND,

        /** Open country: nothing near enough. The full code needs no reference. */
        NONE,

        /** The archive could not be read, which is not the same as nothing near. */
        UNREACHABLE,
    }
}

/** The area around the current place, and what is kept of it and of everywhere. */
data class Keeping(
    /** The area's cell, or null while the archive is not known and there is nothing to offer. */
    val area: String? = null,
    /** The area as kept, when it is. */
    val here: KeptLandmarks.Area? = null,
    val northSouthKm: Int = 0,
    val eastWestKm: Int = 0,
    val busy: Boolean = false,
    val note: Note? = null,
    /** Every area kept, anywhere. */
    val areas: Int = 0,
    val bytes: Long = 0,
) {
    enum class Note { FAILED, FORGOTTEN }
}

/** What the search field has turned up by name. */
data class Finding(
    val query: String = "",
    val places: List<Named> = emptyList(),
    val status: Status = Status.IDLE,
) {
    enum class Status {
        IDLE,
        LOOKING,
        FOUND,

        /** Nothing in the index is called that. */
        MISSING,

        /** The index could not be reached, which is not the same as nothing found. */
        OFFLINE,
    }
}
