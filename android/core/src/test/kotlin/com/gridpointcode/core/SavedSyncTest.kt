package com.gridpointcode.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Saved places as they travel between the phone and the watch. */
class SavedSyncTest {

    private val places = listOf(
        SavedPlace("G3RJM98NM9", "Home", "Blue gate, second door on the left", 3),
        SavedPlace("G3RJN98NM9", "Caf\u00e9 Rosa", "", 2),
        SavedPlace("G3RJM98N00", "", "", 1),
    )

    @Test
    fun aListArrivesAsItLeft() {
        assertEquals(places, decodeSaved(encodeSaved(places)))
        assertEquals(emptyList(), decodeSaved(encodeSaved(emptyList())))
    }

    @Test
    fun theFieldsSeparatorsInsideAFieldArriveExactly() {
        val awkward = listOf(SavedPlace("G3RJM98NM9", "tab\there", "line\nbreak and a \\ backslash\r", 9))
        assertEquals(awkward, decodeSaved(encodeSaved(awkward)))
        assertTrue(encodeSaved(awkward).lines().size == 3, "one header, one place, and the end")
    }

    @Test
    fun aLineThatIsNotAPlaceIsLeftOutNotTheRest() {
        val text = encodeSaved(places) + "not a place\n" + "XXXXXXXXXX\t1\tBad\t\n" + "G3RJM98NM9\tlater\tBad\t\n"
        assertEquals(places, decodeSaved(text))
    }

    @Test
    fun textWithoutTheHeaderIsNoList() {
        assertEquals(emptyList(), decodeSaved("G3RJM98NM9\t1\tHome\t\n"))
        assertEquals(emptyList(), decodeSaved("gpc-saved 2\nG3RJM98NM9\t1\tHome\t\n"), "another version is not read as this one")
        assertEquals(emptyList(), decodeSaved(""))
    }

    @Test
    fun aDamagedListIsNotTakenButAnEmptiedOneIs() {
        assertEquals(places, decodeSavedList(encodeSaved(places)))
        assertEquals(emptyList(), decodeSavedList(encodeSaved(emptyList())), "every place removed on purpose")
        assertEquals(null, decodeSavedList("gpc-saved 1\nnot a place\n"), "lines none of which read")
        assertEquals(null, decodeSavedList("gpc-saved 2\n"))
        assertEquals(null, decodeSavedList(""))
    }

    @Test
    fun whatTheWatchSavedIsMergedLaterSavingWinning() {
        val fromWatch = listOf(
            SavedPlace("G3RJM98NMC", "Door", "", 5),
            SavedPlace("G3RJM98NM9", "Old name", "", 1),
            SavedPlace("G3RJN98NM9", "Renamed on the watch", "", 7),
        )
        val merged = places.merging(fromWatch)
        assertEquals(listOf("G3RJN98NM9", "G3RJM98NMC", "G3RJM98NM9", "G3RJM98N00"), merged.map { it.code })
        assertEquals("Home", merged.savedAt("G3RJM98NM9")?.label, "the phone's later saving is kept")
        assertEquals("Renamed on the watch", merged.savedAt("G3RJN98NM9")?.label, "the watch's later saving wins")
        assertEquals(4, merged.size, "one place per code")
    }
}
