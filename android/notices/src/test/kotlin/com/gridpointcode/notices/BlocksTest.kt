package com.gridpointcode.notices

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Notices laid out for a small screen, and the notices both apps ship. */
class BlocksTest {

    private val licences = File("src/main/assets/licences")

    @Test
    fun aHardWrappedParagraphIsJoinedButAListKeepsItsItems() {
        val blocks = blocksOf(
            "Redistribution and use in source and binary forms, with or without\n" +
                "modification, are permitted provided that the following conditions are\nmet:\n\n" +
                "* Redistributions of source code must retain the above copyright\n  notice.\n" +
                "* Redistributions in binary form must reproduce the above copyright\n  notice.\n",
        )
        assertEquals(
            listOf(
                Block.Paragraph(
                    "Redistribution and use in source and binary forms, with or without modification, " +
                        "are permitted provided that the following conditions are met:",
                ),
                Block.Paragraph(
                    "* Redistributions of source code must retain the above copyright notice.\n" +
                        "* Redistributions in binary form must reproduce the above copyright notice.",
                ),
            ),
            blocks,
        )
    }

    @Test
    fun headingsRulesAndLinksReadAsText() {
        val blocks = blocksOf(
            "### [kdbush.hpp](https://github.com/mourner/kdbush.hpp) by Vladimir Agafonkin\n\n" +
                "===========================================================================\n\n" +
                "URL: [https://square.github.io/okhttp/](https://square.github.io/okhttp/)\n" +
                "License: [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt)\n",
        )
        assertEquals(
            listOf(
                Block.Heading("kdbush.hpp (https://github.com/mourner/kdbush.hpp) by Vladimir Agafonkin"),
                Block.Rule,
                Block.Paragraph(
                    "URL: https://square.github.io/okhttp/ " +
                        "License: The Apache Software License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0.txt)",
                ),
            ),
            blocks,
        )
    }

    @Test
    fun aRuleNextToTextIsStillARule() {
        // How the font licence sets its title.
        val blocks = blocksOf(
            "-----------------------------------------------------------\n" +
                "SIL OPEN FONT LICENSE Version 1.1 - 26 February 2007\n" +
                "-----------------------------------------------------------\n",
        )
        assertEquals(listOf(Block.Rule, Block.Paragraph("SIL OPEN FONT LICENSE Version 1.1 - 26 February 2007"), Block.Rule), blocks)
    }

    @Test
    fun thePlayServicesNoticesLayOut() {
        val blocks = blocksOf(File(licences, "play-services.txt").readText())
        assertTrue(blocks.count { it is Block.Heading } > 20, "a heading for each component compiled into the libraries")
        assertTrue(blocks.none { it is Block.Paragraph && it.text.contains("](") }, "no markdown link left unread")
    }
}
