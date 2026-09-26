package com.gridpointcode.core

import ca.pranavpatel.algo.gridpointcode.GPC
import java.util.Locale

/**
 * Which written form a row holds.
 *
 * The rows carry a key rather than a label because the label and its caution
 * belong to the interface, where they can be translated. A form is a fact about
 * the code; what to call it is not.
 */
enum class FormKey {
    /** The last five characters, unique inside their level-five cell. */
    SHORT,

    /** The code with its check character, which is what anything dictated wants. */
    CHECK,

    /** The forty-eight bit integer, six bytes, order preserving. */
    INTEGER,

    /** Degrees, minutes and seconds, for people and paperwork that expect them. */
    DMS,

    /** A geo URI, which other applications on the device understand. */
    GEO_URI,
}

/** One written form of a selection, ready to show or copy. */
data class Form(val key: FormKey, val value: String)

/**
 * Every form of a code, computed in one pass.
 *
 * One pass on purpose: a panel that fetches each row when it draws it is a panel
 * that can show one row from the place before last. They are cheap, they are
 * pure, and they are always replaced together.
 */
fun forms(
    selection: Selection,
    locale: Locale = Locale.getDefault(),
): List<Form> = listOf(
    Form(FormKey.SHORT, shortForm(selection.code)),
    Form(FormKey.CHECK, GPC.WithCheck(selection.code)),
    Form(FormKey.INTEGER, grouped(GPC.ToInteger(selection.code), locale)),
    Form(FormKey.DMS, GPC.ToDMS(selection.point.latitude, selection.point.longitude)),
    Form(FormKey.GEO_URI, GPC.ToGeoURI(selection.point.latitude, selection.point.longitude)),
)

/** The code as it is printed: a hash, five characters, a hyphen, five more. */
fun formatted(code: String): String = GPC.FormatGPC(code)

/**
 * The short form as it is written: the last five characters after a hyphen,
 * as the site writes it, so it cannot be taken for a whole code.
 */
fun shortForm(code: String): String = "-" + GPC.Shorten(code)

/** Thousands grouped the way the reader's own language groups them. */
internal fun grouped(value: Long, locale: Locale): String =
    String.format(locale, "%,d", value)
