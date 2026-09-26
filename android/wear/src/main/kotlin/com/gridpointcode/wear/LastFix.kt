package com.gridpointcode.wear

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester

/**
 * The last place the app found, for the tile and the complication.
 *
 * Neither of them asks where the watch is: the watch is found only while the
 * app is open, as the privacy page says. So they show what the app last found,
 * with when, and after an hour they stop showing it and offer to open the app,
 * because an old code on the wrist reads as where the wrist is now.
 *
 * @property code the ten characters, no hash
 * @property metres how far to trust the fix, in whole metres
 * @property at when it was found, in milliseconds since the epoch
 */
data class LastFix(val code: String, val metres: Int, val at: Long) {

    /** Until when it is shown. */
    val shownUntil: Long get() = at + SHOWN_FOR_MS

    fun shown(now: Long): Boolean = now in at until shownUntil

    companion object {
        /** An hour: long enough to glance at, short enough not to mislead. */
        const val SHOWN_FOR_MS = 60 * 60 * 1000L

        /** How often the same code is written again, so its time stays close to now while the app is open. */
        const val REFRESH_MS = 60 * 1000L
    }
}

/**
 * Whether [fix] should replace this one: a new code or a tighter fix at once,
 * and the same one again once a minute has passed, so the tile and the
 * complication are asked to redraw when what they show changes, not with every
 * fix the watch gives, which is one every two seconds.
 */
fun LastFix?.replacedBy(fix: LastFix): Boolean =
    this == null || fix.code != code || fix.metres < metres || fix.at - at >= LastFix.REFRESH_MS

/** Asks the tile and the complication to draw the last fix again. */
fun redrawTileAndComplication(context: Context) {
    TileService.getUpdater(context).requestUpdate(CodeTile::class.java)
    ComplicationDataSourceUpdateRequester.create(context, ComponentName(context, CodeComplication::class.java)).requestUpdateAll()
}

/** A fix this close is inside the one cell its code names. */
internal const val INSIDE_ONE_CELL = 2

/** How far to trust a fix, said the same on the Here screen and on the tile. */
fun accuracyText(context: Context, metres: Int): String =
    if (metres <= INSIDE_ONE_CELL) context.getString(R.string.accuracy_inside) else context.getString(R.string.accuracy, metres)

/** Where the last fix is kept: the app's own preferences, never backed up. */
class LastFixStore(context: Context) {

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences("last-fix", Context.MODE_PRIVATE)

    fun read(): LastFix? {
        val code = preferences.getString(CODE, null) ?: return null
        return LastFix(code, preferences.getInt(METRES, 0), preferences.getLong(AT, 0L))
    }

    fun write(fix: LastFix) {
        preferences.edit().putString(CODE, fix.code).putInt(METRES, fix.metres).putLong(AT, fix.at).apply()
    }

    private companion object {
        const val CODE = "code"
        const val METRES = "metres"
        const val AT = "at"
    }
}
