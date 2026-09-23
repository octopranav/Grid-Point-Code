package com.gridpointcode.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Finding a place by name, read the way the website's index is read.
 *
 * The lines below are the live file's own, taken from it for these names, with
 * the region numbers renumbered into a short list. Each test builds the file and
 * its sparse table as the builder does, at a stride small enough to put the
 * block boundaries where they matter, and serves byte ranges of it.
 */
class NamesTest {

    private val regions = listOf(
        "Ontario, Canada",
        "New South Wales, Australia",
        "Ohio, United States",
        "South Dakota, United States",
        "Thies, Senegal",
        "Savanes District, Ivory Coast",
        "California, United States",
        "Munster, Ireland",
    )

    private val lines = listOf(
        "tor\t8\tTor\tH67JLNLP42\t4",
        "tora\t5\tTora\tH52CKP1N50\t5",
        "toronto\t0\tToronto\tG3RJF4318R\t0",
        "toronto\t5\tToronto\t6LKJ5C7TM1\t1",
        "toronto\t5\tToronto\tG3DFC2005T\t2",
        "toronto\t7\tToronto\tG1XK11L8MD\t3",
        "toronto zoo\t9\tToronto Zoo\tG3T9JCW28F\t0",
        "torrance\t2\tTorrance\tG956TR7H8P\t6",
        "tra mhor\t4\tTrá Mhór\tR0LPPF7W9W\t7",
        "tralee\t4\tTralee\tR0X01XWD8M\t7",
    )

    /** The file and its table, with a mark every [stride] lines, and a count of the reads made. */
    private inner class Index(stride: Int, private val failFrom: Int = Int.MAX_VALUE) : NameFile {
        private val bytes: ByteArray
        val table: NameTable
        var reads = 0
            private set

        init {
            assertEquals(lines.sorted(), lines, "the fixture must be in the file's order")
            val keys = mutableListOf<String>()
            val starts = mutableListOf<Long>()
            var offset = 0L
            val text = StringBuilder()
            lines.forEachIndexed { i, line ->
                if (i % stride == 0) {
                    keys += line.substringBefore('\t')
                    starts += offset
                }
                text.append(line).append('\n')
                offset += (line + "\n").toByteArray(Charsets.UTF_8).size
            }
            bytes = text.toString().toByteArray(Charsets.UTF_8)
            table = NameTable(bytes.size.toLong(), regions, keys, starts.toLongArray())
        }

        override fun read(from: Long, until: Long): String? {
            reads += 1
            if (reads >= failFrom) return null
            return bytes.copyOfRange(from.toInt(), until.toInt()).toString(Charsets.UTF_8)
        }

        fun find(query: String) = findNamed(query, table, this)
    }

    @Test
    fun aNameIsFoldedAsTheBuilderFoldsIt() {
        // Every answer is what web/scripts/build-names.mjs gives for the same name.
        val builder = mapOf(
            "Dublin" to "dublin",
            "DUBLIN" to "dublin",
            "  Dublin  " to "dublin",
            "Saint John's Point" to "saint john s point",
            "Trá Mhór" to "tra mhor",
            "Ó Briain" to "o briain",
            "Stratford-upon-Avon" to "stratford upon avon",
            "St. Mary\u2019s" to "st mary s",
            "Baile Átha Cliath" to "baile atha cliath",
            "  " to "",
            "123 Main" to "123 main",
            "Kraków" to "krakow",
            "\u0130stanbul" to "istanbul",
            "\u03a9\u03bc\u03ad\u03b3\u03b1" to "",
            "A  double   space" to "a double space",
            "Ångström" to "angstrom",
            "São Tomé" to "sao tome",
            "\u1e9e" to "",
            "\ufb01nal" to "final",
            // A letterlike capital that only decomposes after lowercasing has
            // had its turn, so the builder drops it. Decomposing first would
            // keep it, and sort this name somewhere the builder never put it.
            "\u210cilbert" to "ilbert",
        )
        builder.forEach { (name, folded) -> assertEquals(folded, fold(name), name) }
    }

    @Test
    fun aNameFindsEveryPlaceCalledThatLargestFirst() {
        val found = Index(stride = 512).find("Toronto")!!
        assertEquals(
            listOf("Ontario, Canada", "New South Wales, Australia", "Ohio, United States", "South Dakota, United States", "Ontario, Canada"),
            found.map { it.region },
        )
        assertEquals(Named("Toronto", "G3RJF4318R", "Ontario, Canada"), found.first())
        assertEquals("Toronto Zoo", found.last().name)
    }

    @Test
    fun aMarkInsideARunDoesNotHideItsStart() {
        // At a stride of three the second mark is the second Toronto, partway
        // through the run. Starting there would lose the one in Ontario.
        val index = Index(stride = 3)
        assertEquals("toronto", index.table.keys[1])
        assertEquals("Ontario, Canada", index.find("toronto")!!.first().region)
    }

    @Test
    fun aRunIsReadAcrossTheBlocksItCrosses() {
        val found = Index(stride = 2).find("toronto")!!
        assertEquals(listOf("G3RJF4318R", "6LKJ5C7TM1", "G3DFC2005T", "G1XK11L8MD", "G3T9JCW28F"), found.map { it.code })
    }

    @Test
    fun theSearchStopsWhereTheRunEnds() {
        val index = Index(stride = 2)
        assertEquals(listOf("Toronto Zoo"), index.find("toronto zoo")!!.map { it.name })
        assertEquals(2, index.reads, "one block before the run, one holding it, and nothing after")
    }

    @Test
    fun aPrefixFindsTheNamesThatBeginWithIt() {
        val index = Index(stride = 3)
        assertEquals(
            listOf("Tor", "Tora", "Toronto", "Toronto", "Toronto", "Toronto", "Toronto Zoo", "Torrance"),
            index.find("tor")!!.map { it.name },
        )
        assertEquals(listOf("Trá Mhór", "Tralee"), index.find("Tra")!!.map { it.name })
        assertEquals(listOf("Trá Mhór"), index.find("Trá Mhór")!!.map { it.name })
    }

    @Test
    fun theLastBlockIsReadToTheEndOfTheFile() {
        val found = Index(stride = 3).find("tralee")!!
        assertEquals(listOf(Named("Tralee", "R0X01XWD8M", "Munster, Ireland")), found)
    }

    @Test
    fun oneLetterIsNotWorthAskingFor() {
        val index = Index(stride = 2)
        assertEquals(emptyList(), index.find("t"))
        assertEquals(emptyList(), index.find(" - "))
        assertEquals(0, index.reads)
    }

    @Test
    fun nothingCalledThatIsAnAnswer() {
        assertEquals(emptyList(), Index(stride = 2).find("zanzibar"))
    }

    @Test
    fun anUnreachableFileIsNotTheSameAsNothingFound() {
        assertNull(Index(stride = 2, failFrom = 1).find("toronto"))
        // Two blocks arrived, then the connection went: what was found stands.
        val partial = Index(stride = 2, failFrom = 3).find("toronto")!!
        assertEquals(listOf("Ontario, Canada", "New South Wales, Australia"), partial.map { it.region })
    }

    @Test
    fun onlyWhatNothingElseReadsIsLookedUpByName() {
        assertTrue(isName("Toronto"))
        assertTrue(isName("Saint John's Point"))
        assertTrue(isName("Trá Mhór"))
        assertFalse(isName("#G3RJF-4318R"), "a code")
        assertFalse(isName("g3rjf 4318r"), "a code, typed loosely")
        assertFalse(isName("43.65, -79.38"), "a point")
        assertFalse(isName("geo:43.65,-79.38"), "a geo URI")
        assertFalse(isName("-4318R"), "a short form")
        assertFalse(isName("T"), "too short to narrow")
    }
}
