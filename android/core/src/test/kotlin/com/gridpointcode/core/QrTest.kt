package com.gridpointcode.core

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The QR code for a place's link: it reads back as the link, and it has room to be found. */
class QrTest {

    /** The symbol as a scanner would see it, a few pixels to a module, read back with the library's own reader. */
    private fun scanned(symbol: QrSymbol): String {
        val scale = 4
        val side = symbol.size * scale
        val pixels = IntArray(side * side) { index ->
            if (symbol.isDark((index % side) / scale, (index / side) / scale)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side, side, pixels)))
        return QRCodeReader().decode(bitmap, mapOf(DecodeHintType.PURE_BARCODE to true)).text
    }

    @Test
    fun theLinkWithItsDirectionsReadsBack() {
        val link = addressOf("G3RJM98NM9", "Blue gate, second door on the left")
        assertEquals(link, scanned(qrFor(link)))
    }

    @Test
    fun theLongestLinkTheAppMakesStillReadsBack() {
        // Directions are capped at eighty characters, and every one of these is
        // escaped in the link, which makes the longest payload there is.
        val link = addressOf("G3RJM98NM9", "\u00e9".repeat(NOTE_LIMIT))
        val symbol = qrFor(link)
        assertEquals(link, scanned(symbol))
        assertTrue(symbol.size <= 101 + 2 * QR_QUIET_ZONE, "still a symbol a phone can take in: ${symbol.size} modules")
    }

    @Test
    fun anAreasLinkReadsBack() {
        val link = areaAddress("G3RJM")
        assertEquals(link, scanned(qrFor(link)))
    }

    @Test
    fun theQuietZoneIsLeftLight() {
        val symbol = qrFor(addressOf("G3RJM98NM9", ""))
        for (i in 0 until symbol.size) {
            for (edge in 0 until QR_QUIET_ZONE) {
                assertTrue(!symbol.isDark(i, edge) && !symbol.isDark(edge, i), "margin module ($i, $edge)")
                assertTrue(!symbol.isDark(i, symbol.size - 1 - edge) && !symbol.isDark(symbol.size - 1 - edge, i))
            }
        }
        // And the finder pattern starts right after it.
        assertTrue(symbol.isDark(QR_QUIET_ZONE, QR_QUIET_ZONE))
    }
}
