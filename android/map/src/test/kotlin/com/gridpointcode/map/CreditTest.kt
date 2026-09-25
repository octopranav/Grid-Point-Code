package com.gridpointcode.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The credit line: what the sources declare, once each, and never nothing. */
class CreditTest {

    private val declared =
        "<a href=\"https://openfreemap.org\" target=\"_blank\">OpenFreeMap</a> " +
            "<a href=\"https://www.openmaptiles.org/\" target=\"_blank\">&copy; OpenMapTiles</a> " +
            "Data from <a href=\"https://www.openstreetmap.org/copyright\" target=\"_blank\">OpenStreetMap</a>"

    @Test
    fun theSourcesOwnCreditIsShown() {
        assertEquals(declared, creditOf(listOf(null, declared)))
    }

    @Test
    fun aCreditTwoSourcesShareIsShownOnce() {
        assertEquals(declared, creditOf(listOf(declared, " $declared ", "")))
    }

    @Test
    fun beforeAnySourceHasSaidTheProvidersLineStandsIn() {
        assertEquals(PROVIDER_CREDIT, creditOf(emptyList()))
        assertEquals(PROVIDER_CREDIT, creditOf(listOf(null, " ")))
    }

    @Test
    fun theStandInCreditsOpenStreetMapWithItsCopyrightPage() {
        assertTrue(PROVIDER_CREDIT.contains("href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a>"))
    }
}
