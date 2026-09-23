package com.gridpointcode.core

/*
 * Reading back a short form given with a landmark:
 * `-98NM9 near Old Toronto, Ontario, Canada`.
 *
 * The place is found by name in the website's name index, then in the landmark
 * archive, whose coordinates are the ones the sender's list was drawn from. The
 * short form is recovered against those, so a line written from the archive
 * reads back to exactly the code it was written for. Both indexes are built by
 * the same filters and write a region the same way, so `Name, Region` as a
 * sender writes it is exactly a row of each.
 */

/** What a reference turned out to name, among the index's places. */
sealed interface ReferenceMatch {
    data class One(val place: Named) : ReferenceMatch

    /** More than one place answers to what was written. */
    data object Several : ReferenceMatch

    data object None : ReferenceMatch
}

/** The part of a reference to look up: the name, before the region. */
fun referenceName(reference: String): String = reference.substringBefore(',').trim()

/**
 * Which place a reference names, among the index's places beginning with its name.
 *
 * Compared as written first, then folded, so a reader who typed it without the
 * accents still arrives. Written `Name, Region`, as this app and the website
 * write it, it names at most one place. A name on its own names a place only
 * when nothing else in the index shares it: a guess between two would be
 * recovered against the wrong one without a sign.
 */
fun matchReference(reference: String, places: List<Named>): ReferenceMatch {
    val given = reference.trim().replace(SPACES, " ")
    val folded = fold(given)
    val tests: List<(Named) -> Boolean> = listOf(
        { described(it) == given },
        { fold(described(it)) == folded },
        { fold(it.name) == folded },
    )
    for (test in tests) {
        val matched = places.filter(test)
        if (matched.size == 1) return ReferenceMatch.One(matched.single())
        if (matched.size > 1) return ReferenceMatch.Several
    }
    return ReferenceMatch.None
}

/**
 * The archive's entry for a place from the index, whose coordinates the short
 * form is recovered against. Absent when the place's name is not unique within
 * its region, which is why the archive left it out.
 */
fun landmarkFor(place: Named, shard: List<Landmark>): Landmark? =
    shard.firstOrNull { it.name == place.name && it.region == place.region }

private fun described(place: Named): String =
    if (place.region.isEmpty()) place.name else "${place.name}, ${place.region}"

private val SPACES = Regex("\\s+")
