package com.gridpointcode.wear

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/**
 * The complication, as the watch face asks for it: the short form of the last
 * place the app found, for an hour, and after that, and before any, an offer to
 * find one. The face is never asked to update to get from one to the other: the
 * timeline carries both, the code's entry ending when its hour does.
 */
@RunWith(AndroidJUnit4::class)
class CodeComplicationTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val complication = Robolectric.buildService(CodeComplication::class.java).create().get()
    private val now = System.currentTimeMillis()

    private fun asked(type: ComplicationType = ComplicationType.SHORT_TEXT) =
        runBlocking { complication.onComplicationRequest(ComplicationRequest(1, type, false)) }

    private fun ComplicationData.words(at: Long = now): String =
        (this as ShortTextComplicationData).text.getTextAt(app.resources, Instant.ofEpochMilli(at)).toString()

    @Test
    fun aRecentFixShowsItsShortFormUntilItsHourIsUp() {
        val found = LastFix("G3RJM8X3L1", 4, now - 10 * 60 * 1000L)
        LastFixStore(app).write(found)
        val timeline = asked()!!
        val entry = timeline.timelineEntries!!.single()
        assertEquals("-8X3L1", entry.complicationData.words())
        // Ended when its hour is, never left open: an open end ended at zero,
        // and the face found nothing to show.
        assertEquals(found.at, entry.validity.start.toEpochMilli())
        assertEquals(found.shownUntil, entry.validity.end.toEpochMilli())
        assertEquals("Find", timeline.defaultComplicationData.words())
    }

    @Test
    fun aScreenReaderHearsTheShortFormSaid() {
        LastFixStore(app).write(LastFix("G3RJM8X3L1", 4, now))
        val data = asked()!!.timelineEntries!!.single().complicationData as ShortTextComplicationData
        assertEquals("Short form -8X3L1", data.contentDescription!!.getTextAt(app.resources, Instant.ofEpochMilli(now)).toString())
    }

    @Test
    fun withNoFixItOffersToFindOne() {
        val timeline = asked()!!
        assertTrue(timeline.timelineEntries.isNullOrEmpty())
        assertEquals("Find", timeline.defaultComplicationData.words())
    }

    @Test
    fun aFixOlderThanAnHourIsNotShown() {
        LastFixStore(app).write(LastFix("G3RJM8X3L1", 4, now - 2 * LastFix.SHOWN_FOR_MS))
        assertTrue(asked()!!.timelineEntries.isNullOrEmpty())
    }

    @Test
    fun onlyShortTextIsOffered() {
        assertNull(asked(ComplicationType.LONG_TEXT))
        assertNull(complication.getPreviewData(ComplicationType.RANGED_VALUE))
    }

    @Test
    fun theFacesPickerShowsTheSpecificationsExample() {
        assertEquals("-98NM9", complication.getPreviewData(ComplicationType.SHORT_TEXT)!!.words())
    }
}
