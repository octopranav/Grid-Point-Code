package com.gridpointcode.wear

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountUpTimeReference
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import androidx.wear.watchface.complications.datasource.ComplicationDataTimeline
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingTimelineComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.TimeInterval
import androidx.wear.watchface.complications.datasource.TimelineEntry
import com.gridpointcode.core.shortForm
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * The complication, from the canvas: the short form of the last place the app
 * found, beside the four bars, on any watch face that has room for a short
 * text. Where the face shows a title, it is how long ago that was, counted by
 * the watch itself.
 *
 * Like the tile, it never asks where the watch is. The last fix is one entry of
 * a timeline that ends after an hour, and after it the complication offers to
 * find where the watch is instead, with no update needed to get there.
 */
class CodeComplication : SuspendingTimelineComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationDataTimeline? {
        if (request.complicationType != ComplicationType.SHORT_TEXT) return null
        val last = LastFixStore(this).read()
        val entries = if (last != null && last.shown(System.currentTimeMillis())) {
            listOf(TimelineEntry(TimeInterval(Instant.ofEpochMilli(last.at), Instant.ofEpochMilli(last.shownUntil)), code(last)))
        } else {
            emptyList()
        }
        return ComplicationDataTimeline(find(), entries)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        if (type == ComplicationType.SHORT_TEXT) code(LastFix(PREVIEW, 5, System.currentTimeMillis())) else null

    private fun code(last: LastFix): ComplicationData {
        val short = shortForm(last.code)
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(short).build(),
            contentDescription = PlainComplicationText.Builder(getString(R.string.complication_said, short)).build(),
        )
            .setTitle(
                TimeDifferenceComplicationText.Builder(TimeDifferenceStyle.SHORT_SINGLE_UNIT, CountUpTimeReference(Instant.ofEpochMilli(last.at)))
                    .setMinimumTimeUnit(TimeUnit.MINUTES)
                    .build(),
            )
            .setMonochromaticImage(bars())
            .setTapAction(openApp())
            .build()
    }

    private fun find(): ComplicationData =
        ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(getString(R.string.complication_find)).build(),
            contentDescription = PlainComplicationText.Builder(getString(R.string.complication_said_none)).build(),
        )
            .setMonochromaticImage(bars())
            .setTapAction(openApp())
            .build()

    private fun bars(): MonochromaticImage = MonochromaticImage.Builder(Icon.createWithResource(this, R.drawable.complication_bars)).build()

    private fun openApp(): PendingIntent =
        PendingIntent.getActivity(this, 0, Intent(this, WatchActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private companion object {
        /** The example the website opens on, for the face's picker. */
        const val PREVIEW = "G3RJM98NM9"
    }
}
