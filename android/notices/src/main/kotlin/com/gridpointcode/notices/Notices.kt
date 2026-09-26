package com.gridpointcode.notices

import android.content.Context

/*
 * The open-source notices. Several of the libraries the phone and the watch are
 * built on are under licences that require their notice to travel with them, so
 * each notice ships in the assets as its authors wrote it, and each app has a
 * page that shows them. Each app's `licences/components.txt` names every library
 * its release ships and its licence; the build's checkNotices task holds that
 * list to the release.
 */

/** One library the release ships: its Maven module, its licence, and the notice it requires, if any. */
data class Component(val module: String, val licence: String, val notice: String?)

/** The libraries named in `components.txt`, in its order. Comments and blank lines are not libraries. */
fun componentsIn(text: String): List<Component> =
    text.lineSequence()
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { line ->
            val parts = line.split(Regex("\\s+"))
            Component(parts[0], parts[1], parts.getOrNull(2)?.substringBefore('@'))
        }
        .toList()

/** A piece of a notice as the page lays it out. */
sealed interface Block {
    data class Heading(val text: String) : Block
    data class Paragraph(val text: String) : Block
    data object Rule : Block
}

/**
 * A notice, written as markdown or plain text, as blocks a phone can lay out.
 *
 * The notices are hard-wrapped for a terminal, so a paragraph's lines are
 * joined and left to wrap to the screen, except where a line starts a list
 * item. A link keeps its address beside its words, because the page is read,
 * not clicked through, and the address is part of the notice.
 */
fun blocksOf(notice: String): List<Block> {
    val blocks = mutableListOf<Block>()
    val paragraph = StringBuilder()
    fun flush() {
        if (paragraph.isNotEmpty()) blocks += Block.Paragraph(paragraph.toString())
        paragraph.clear()
    }
    for (raw in notice.replace("\r\n", "\n").lines()) {
        val line = raw.trimEnd()
        when {
            line.isBlank() -> flush()
            // The markdown notices fence each licence as code; the fence is
            // markup, not words, and ends whatever came before it.
            line.trim().startsWith("```") -> flush()
            // A rule can sit on the line next to text, as the font licence
            // sets its title between two; it is still a rule.
            line.trim().matches(RULE) -> {
                flush()
                if (blocks.lastOrNull() != Block.Rule) blocks += Block.Rule
            }
            line.startsWith("#") -> {
                flush()
                blocks += Block.Heading(linked(line.trimStart('#').trim()))
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(if (ITEM.containsMatchIn(line)) "\n" else " ")
                paragraph.append(linked(line.trim()))
            }
        }
    }
    flush()
    return blocks
}

private val RULE = Regex("[=\\-_*]{3,}")

private val ITEM = Regex("^\\s*([*\\-]|\\d+\\.|\\(\\w\\))\\s")

/** Markdown links as words and address: `[words](address)` becomes "words (address)", or the address alone. */
private fun linked(text: String): String =
    Regex("\\[([^\\]]*)]\\(([^)]*)\\)").replace(text) { match ->
        val (words, address) = match.destructured
        if (words == address) address else "$words ($address)"
    }

/** A notice from the app's assets, or null if it is missing. */
fun readNotice(context: Context, name: String): String? =
    runCatching { context.assets.open("licences/$name").bufferedReader().use { it.readText() } }.getOrNull()
