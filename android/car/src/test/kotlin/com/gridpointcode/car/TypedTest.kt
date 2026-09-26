package com.gridpointcode.car

import com.gridpointcode.core.Point
import com.gridpointcode.core.Problem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The car's box reads what the phone reads, and refuses a short form it has nothing to read against. */
class TypedTest {

    private val toronto = Point(43.650006, -79.380004)

    @Test
    fun aCodeCoordinatesAndALinkOpenAsOnThePhone() {
        assertEquals("G3RJM98NM9", (typed("#G3RJM-98NM9", null) as Typed.Place).selection.code)
        assertEquals("G3RJM98NM9", (typed("43.650006, -79.380004", null) as Typed.Place).selection.code)
        val linked = typed("https://gridpointcode.com/play?c=G3RJM98NM9&n=Blue+gate", null) as Typed.Place
        assertEquals("G3RJM98NM9", linked.selection.code)
        assertEquals("Blue gate", linked.note)
    }

    @Test
    fun aShortFormIsReadAgainstWhereTheCarIsAndNowhereElse() {
        assertEquals(Typed.NeedsFix, typed("-98NM9", null), "with no fix there is nothing to read it against")
        assertEquals("G3RJM98NM9", (typed("-98NM9", toronto) as Typed.Place).selection.code)
    }

    @Test
    fun whatTheCarCannotDoIsSaid() {
        assertEquals(Typed.Area, typed("G3RJM", toronto))
        assertEquals(Typed.Anchored, typed("-98NM9 near Old Toronto, Ontario, Canada", toronto))
        assertIs<Problem.Unread>((typed("not a code", toronto) as Typed.Refused).problem)
        assertIs<Problem.Unread>((typed("", null) as Typed.Refused).problem)
    }
}
