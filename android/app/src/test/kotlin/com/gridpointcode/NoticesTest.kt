package com.gridpointcode

import com.gridpointcode.notices.Block
import com.gridpointcode.notices.Component
import com.gridpointcode.notices.blocksOf
import com.gridpointcode.notices.componentsIn
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The phone's notices: the list of libraries read as the page reads it, and the map's notice laid out. */
class NoticesTest {

    private val licences = File("src/main/assets/licences")
    private val shipped = listOf(licences, File("../notices/src/main/assets/licences"), File("../designsystem/src/main/assets/licences"))

    @Test
    fun everyLibraryHasALicenceAndEveryNoticeItNamesShips() {
        val components = componentsIn(File(licences, "components.txt").readText())
        assertTrue(components.size > 100, "the release ships over a hundred libraries")
        assertTrue(components.all { it.licence in setOf("Apache-2.0", "BSD-2-Clause", "Android-SDK-License") })
        for (component in components) {
            component.notice?.let { notice -> assertTrue(shipped.any { File(it, notice).exists() }, "${component.module} names $notice") }
        }
        assertEquals(
            Component("org.maplibre.gl:android-sdk", "BSD-2-Clause", "maplibre-native-android.md"),
            components.first { it.module == "org.maplibre.gl:android-sdk" },
        )
    }

    @Test
    fun theMapLibrarysWholeNoticeLaysOut() {
        val blocks = blocksOf(File(licences, "maplibre-native-android.md").readText())
        assertTrue(blocks.size > 300)
        assertTrue(blocks.any { it is Block.Paragraph && it.text.contains("Copyright (c) 2021 MapLibre contributors") })
        assertTrue(blocks.none { it is Block.Paragraph && it.text.contains("](") }, "no markdown link left unread")
        assertTrue(blocks.none { it is Block.Paragraph && it.text.contains("```") }, "no code fence left in the words")
    }
}
