package com.gridpointcode.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** Country packs of the name index: searched together, they answer as the whole file does. */
class PacksTest {

    private val regions = listOf("Ontario, Canada", "New South Wales, Australia", "Ohio, United States", "Munster, Ireland")

    /** The index's lines, in its order. */
    private val lines = listOf(
        "tor\t8\tTor\tH67JLNLP42\t3",
        "toronto\t0\tToronto\tG3RJF4318R\t0",
        "toronto\t5\tToronto\t6LKJ5C7TM1\t1",
        "toronto\t7\tToronto\tG1XK11L8MD\t2",
        "toronto zoo\t9\tToronto Zoo\tG3T9JCW28F\t0",
        "tra mhor\t4\tTrá Mhór\tR0LPPF7W9W\t3",
        "tralee\t4\tTralee\tR0X01XWD8M\t3",
    )

    /** A file of [lines] with its table built as the app builds a kept pack's. */
    private fun source(lines: List<String>, stride: Int = 2): NameSource {
        val marks = NameMarks(stride)
        lines.forEach(marks::add)
        val bytes = lines.joinToString("") { it + "\n" }.toByteArray(Charsets.UTF_8)
        assertEquals(bytes.size.toLong(), marks.bytes, "the table counts the bytes the file has, in UTF-8")
        return NameSource(marks.table(regions)) { from, until -> bytes.copyOfRange(from.toInt(), until.toInt()).toString(Charsets.UTF_8) }
    }

    /** The lines of one country, as the builder writes its pack: the index's lines, in its order. */
    private fun pack(country: String) = lines.filter { regions[it.substringAfterLast('\t').toInt()].endsWith(country) }

    @Test
    fun packsSearchedTogetherAnswerAsTheWholeFileDoes() {
        val whole = source(lines)
        val packs = listOf("Ireland", "United States", "Australia", "Canada").map { source(pack(it)) }
        for (query in listOf("toronto", "tor", "tra", "tralee", "nowhere")) {
            assertEquals(findNamed(query, whole.table, whole.file), findNamedAcross(query, packs), query)
        }
        assertEquals("Ontario, Canada", findNamedAcross("toronto", packs).first().region, "the largest Toronto first, from its own pack")
    }

    @Test
    fun onlyTheKeptCountriesAnswer() {
        val ireland = listOf(source(pack("Ireland")))
        assertEquals(listOf("Tor"), findNamedAcross("tor", ireland).map { it.name }, "no Toronto, since no pack holding one is kept")
        assertEquals(listOf("Trá Mhór", "Tralee"), findNamedAcross("tra", ireland).map { it.name })
    }

    @Test
    fun theTableFallsEveryStrideLinesAsTheBuildersDoes() {
        val marks = NameMarks(stride = 3)
        lines.forEach(marks::add)
        val table = marks.table(regions)
        assertEquals(listOf("tor", "toronto", "tralee"), table.keys)
        var offset = 0L
        val expected = lines.mapIndexedNotNull { i, line ->
            (if (i % 3 == 0) offset else null).also { offset += (line + "\n").toByteArray(Charsets.UTF_8).size }
        }
        assertContentEquals(expected.toLongArray(), table.starts)
        assertEquals(lines.size, marks.lines)
    }

    @Test
    fun aPackThatCannotBeReadGivesNothingAndTheOthersStillAnswer() {
        val broken = NameSource(source(pack("Canada")).table) { _, _ -> null }
        assertEquals(listOf("United States"), findNamedAcross("toronto", listOf(broken, source(pack("United States")))).map { it.region.substringAfter(", ") })
    }
}
