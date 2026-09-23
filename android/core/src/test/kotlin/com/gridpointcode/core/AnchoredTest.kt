package com.gridpointcode.core

import ca.pranavpatel.algo.gridpointcode.GPC
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Reading back a short form given with a landmark.
 *
 * The index rows and archive entries are the live ones for these names. Salem is
 * the case the archive exists to catch: the index keeps one row for "Salem,
 * Ontario, Canada", and the archive has none, because Ontario has more than one
 * Salem and a line naming it could be read against either.
 */
class AnchoredTest {

    /** The specification's example, `#G3RJM-98NM9`. */
    private val example = Point(43.650006, -79.380004)
    private val exampleCode = "G3RJM98NM9"

    private val start = PlaceState(selectionAt(Point(51.5007, -0.1246), Source.SAMPLE))
    private val line = "-98NM9 near Old Toronto, Ontario, Canada"

    private val index = listOf(
        Named("Old Toronto", "G3RJM97793", "Ontario, Canada"),
        Named("CN Tower", "G3RJM0MGFW", "Ontario, Canada"),
        Named("Trá Mhór", "R0LPPF7W9W", "Munster, Ireland"),
        Named("Scarborough", "R944NWHWD5", "England, United Kingdom"),
        Named("Scarborough", "G40K2779GG", "Maine, United States"),
        Named("Scarborough", "TL9J99J9J9", "Manitoba, Canada"),
        Named("Salem", "G3R9N1142J", "Ontario, Canada"),
    )

    private val shard = listOf(
        Landmark("CN Tower", 43.64213, -79.38704, "Ontario, Canada", LandmarkKind.STRUCTURE),
        Landmark("Old Toronto", 43.64999, -79.38206, "Ontario, Canada", LandmarkKind.PLACE),
    )

    @Test
    fun theLineTheAppSharesIsReadAsAShortFormAndItsPlace() {
        assertEquals(Reading.Anchored("-98NM9", "Old Toronto, Ontario, Canada"), read(line))
        assertFalse(isName(line), "not something to look up by name as it stands")
    }

    @Test
    fun aLineRetypedByHandStillReads() {
        assertEquals(Reading.Anchored("-98NM9", "old toronto, ontario, canada"), read("98nm9 NEAR old toronto, ontario, canada"))
        assertEquals(Reading.Anchored("-98NM9", "CN Tower"), read("  -98NM9   near   CN Tower "))
    }

    @Test
    fun notEveryNearIsAnAnchor() {
        assertIs<Reading.Unread>(read("near Old Toronto"))
        assertIs<Reading.Short>(read("-98NM9"))
        assertIs<Reading.Unread>(read("-98NM9 nearby"))
        assertIs<Reading.Unread>(read("-98NM9 near"))
    }

    @Test
    fun theNameIsWhatIsLookedUp() {
        assertEquals("Old Toronto", referenceName("Old Toronto, Ontario, Canada"))
        assertEquals("CN Tower", referenceName("CN Tower"))
    }

    @Test
    fun theReferenceIsFoundAsWritten() {
        assertEquals(ReferenceMatch.One(index[0]), matchReference("Old Toronto, Ontario, Canada", index))
    }

    @Test
    fun aReferenceTypedWithoutAccentsOrCapitalsIsStillFound() {
        assertEquals(ReferenceMatch.One(index[2]), matchReference("Tra Mhor, Munster, Ireland", index))
        assertEquals(ReferenceMatch.One(index[0]), matchReference("old  toronto,  ontario, canada", index))
    }

    @Test
    fun aNameAloneIsEnoughOnlyWhenNothingElseHasIt() {
        assertEquals(ReferenceMatch.One(index[1]), matchReference("CN Tower", index))
        assertEquals(ReferenceMatch.Several, matchReference("Scarborough", index))
    }

    @Test
    fun theWrongRegionIsNotAGuess() {
        assertEquals(ReferenceMatch.None, matchReference("Old Toronto, Quebec, Canada", index))
        // The live index has no Scarborough in Ontario at all.
        assertEquals(ReferenceMatch.None, matchReference("Scarborough, Ontario, Canada", index))
    }

    @Test
    fun theArchiveGivesTheCoordinatesTheSenderUsed() {
        val landmark = landmarkFor(index[0], shard)
        assertEquals(shard[1], landmark)
        // The index and the archive agree about where it is.
        assertEquals(index[0].code, GPC.Encode(landmark!!.latitude, landmark.longitude, false))
        assertNull(landmarkFor(index.single { it.name == "Salem" }, shard))
    }

    @Test
    fun readingItBackGoesToTheCodeAndDropsTheOldDirections() {
        val there = start.described("Blue gate").locating()
        val read = there.anchoredAt("-98NM9", shard[1])
        assertEquals(exampleCode, read.selection.code)
        assertEquals(Source.ANCHORED, read.selection.source)
        assertEquals("", read.note)
        assertEquals(Locating.IDLE, read.locating)
        assertNull(read.problem)
    }

    @Test
    fun aShortFormThatWillNotReadLeavesThePlaceWhereItWas() {
        val confused = start.anchoredAt("-QQQYY", shard[1])
        assertEquals(start.selection, confused.selection)
        assertIs<Problem.UnreadShort>(confused.problem)
    }

    @Test
    fun aLandmarkThatCannotBeUsedLeavesThePlaceWithTheReason() {
        val reading = read("-98NM9 near Salem, Ontario, Canada") as Reading.Anchored
        val refused = start.unanchored(reading, Problem.Unanchored.Why.NOT_UNIQUE)
        assertEquals(start.selection, refused.selection)
        assertEquals(Problem.Unanchored("-98NM9", "Salem, Ontario, Canada", Problem.Unanchored.Why.NOT_UNIQUE), refused.problem)
    }

    @Test
    fun anAnchoredLineIsNeverReadAgainstWhereverTheScreenIs() {
        // Five characters recovered against London would name somewhere in
        // London, plausibly and wrongly. Without the landmark looked up, nothing
        // moves.
        val opened = start.opened(line, Source.CODE)
        assertEquals(start.selection, opened.selection)
        assertIs<Problem.Unanchored>(opened.problem)
    }

    @Test
    fun everyLineTheAppWritesReadsBackToItsCode() {
        val toronto = listOf(
            Landmark("Old Toronto", 43.64999, -79.38206, "Ontario, Canada", LandmarkKind.PLACE),
            Landmark("Downtown Toronto", 43.65011, -79.3829, "Ontario, Canada", LandmarkKind.PLACE),
            Landmark("Toronto Old City Hall", 43.65249, -79.38201, "Ontario, Canada", LandmarkKind.STRUCTURE),
            Landmark("Financial District", 43.64713, -79.38094, "Ontario, Canada", LandmarkKind.PLACE),
            Landmark("Nathan Phillips Square", 43.6523, -79.38345, "Ontario, Canada", LandmarkKind.STRUCTURE),
            Landmark("Hockey Hall of Fame", 43.64695, -79.37743, "Ontario, Canada", LandmarkKind.STRUCTURE),
            Landmark("CN Tower", 43.64213, -79.38704, "Ontario, Canada", LandmarkKind.STRUCTURE),
        )
        val rows = toronto.map { Named(it.name, GPC.Encode(it.latitude, it.longitude, false), it.region) }

        val anchors = anchorsFor(example, toronto)
        assertEquals(toronto.size, anchors.size)
        for (anchor in anchors) {
            val written = anchored(exampleCode, anchor.landmark)
            val reading = assertIs<Reading.Anchored>(read(written))
            val match = assertIs<ReferenceMatch.One>(matchReference(reading.reference, rows))
            val landmark = landmarkFor(match.place, toronto)!!
            assertEquals(exampleCode, start.anchoredAt(reading.short, landmark).selection.code, written)
        }
    }
}
