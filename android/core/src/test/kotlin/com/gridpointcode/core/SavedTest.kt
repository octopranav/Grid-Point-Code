package com.gridpointcode.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** What saving, forgetting and finding a saved place do, and what opening one brings back. */
class SavedTest {

    private val start = PlaceState(selectionAt(Point(51.5007, -0.1246), Source.SAMPLE))

    private val market = SavedPlace("G3RJM8X6TX", "St. Lawrence Market", "Front entrance", savedAt = 1)
    private val cafe = SavedPlace("G3RJM98NM9", "Café Rosa", "Side door on Adelaide", savedAt = 2)

    @Test
    fun theNewestComesFirstAndACodeIsSavedOnce() {
        val saved = emptyList<SavedPlace>().saving(market).saving(cafe)
        assertEquals(listOf(cafe, market), saved)
        val renamed = saved.saving(market.copy(label = "The market", savedAt = 3))
        assertEquals(listOf("The market", "Café Rosa"), renamed.map { it.label })
    }

    @Test
    fun aNameIsKeptAsOneTidyLine() {
        val saved = emptyList<SavedPlace>().saving(market.copy(label = "  St.  Lawrence\n Market  "))
        assertEquals("St. Lawrence Market", saved.single().label)
        assertEquals(LABEL_LIMIT, tidyLabel("x".repeat(200)).length)
    }

    @Test
    fun forgettingRemovesOnlyThatPlace() {
        val saved = listOf(cafe, market).forgetting(cafe.code)
        assertEquals(listOf(market), saved)
        assertNull(saved.savedAt(cafe.code))
        assertEquals(market, saved.savedAt(market.code))
    }

    @Test
    fun aSavedPlaceIsFoundByAnyWordOfItsNameOrDirections() {
        val saved = listOf(cafe, market)
        assertEquals(listOf(cafe), matchSaved("cafe", saved), "without its accent")
        assertEquals(listOf(market), matchSaved("LAWRENCE", saved), "a word that is not the first")
        assertEquals(listOf(cafe), matchSaved("adelaide", saved), "in the directions")
        assertEquals(emptyList(), matchSaved("  ", saved))
    }

    @Test
    fun goTakesTheSavedPlaceWhoseWholeNameWasTyped() {
        val saved = listOf(cafe, market)
        assertEquals(market, namedSaved("st lawrence market", saved))
        assertNull(namedSaved("lawrence", saved), "part of a name is a search, not a choice")
    }

    @Test
    fun openingASavedPlaceBringsItsOwnDirectionsBack() {
        val elsewhere = start.described("Blue gate").locating()
        val opened = elsewhere.recalled(market)
        assertEquals(market.code, opened.selection.code)
        assertEquals(Source.SAVED, opened.selection.source)
        assertEquals("Front entrance", opened.note)
        assertEquals(Locating.IDLE, opened.locating)
    }

    @Test
    fun aSavedCodeThatWillNotReadLeavesThePlaceWhereItWas() {
        val confused = start.recalled(market.copy(code = "QQQQQYYYYY"))
        assertEquals(start.selection, confused.selection)
        assertIs<Problem.Unread>(confused.problem)
    }
}
