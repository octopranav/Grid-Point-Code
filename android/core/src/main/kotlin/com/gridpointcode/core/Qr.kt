package com.gridpointcode.core

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/*
 * A QR code for the link to a place, as the website draws one.
 *
 * The payload is the permalink, directions and all, not the code and not a geo
 * URI: scanning it opens the place, which is the thing being handed over, on any
 * phone, with or without this app. Error correction M, fifteen per cent, because
 * a code on a screen is photographed at an angle, under a reflection, by a
 * camera not quite in focus; and four modules of quiet zone, the least the
 * standard allows, without which a scanner cannot find the symbol's edge.
 */

/** A QR symbol: [size] modules square, quiet zone included, each dark or light. */
class QrSymbol internal constructor(val size: Int, private val dark: BooleanArray) {
    fun isDark(x: Int, y: Int): Boolean = dark[y * size + x]
}

/** Modules of light margin around the symbol, the standard's minimum. */
const val QR_QUIET_ZONE = 4

/** The QR symbol for [link]. */
fun qrFor(link: String): QrSymbol {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to QR_QUIET_ZONE,
        EncodeHintType.CHARACTER_SET to "UTF-8",
    )
    // Asked for no size at all, the writer draws one pixel to a module.
    val matrix = QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, 0, 0, hints)
    val size = matrix.width
    return QrSymbol(size, BooleanArray(size * size) { matrix.get(it % size, it / size) })
}
