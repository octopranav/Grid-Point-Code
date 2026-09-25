package com.gridpointcode

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * A crosshair for the locate button: a ring, a dot, and four ticks. Drawn here
 * rather than taken from an icon library, which would add a large dependency
 * for one symbol.
 */
val Crosshair: ImageVector = ImageVector.Builder(
    name = "Crosshair",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round) {
        moveTo(18.5f, 12f)
        arcTo(6.5f, 6.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 5.5f, y1 = 12f)
        arcTo(6.5f, 6.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 18.5f, y1 = 12f)
        close()
        moveTo(12f, 2.5f)
        lineTo(12f, 5.5f)
        moveTo(12f, 18.5f)
        lineTo(12f, 21.5f)
        moveTo(2.5f, 12f)
        lineTo(5.5f, 12f)
        moveTo(18.5f, 12f)
        lineTo(21.5f, 12f)
    }
    path(fill = SolidColor(Color.Black)) {
        moveTo(14.2f, 12f)
        arcTo(2.2f, 2.2f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 9.8f, y1 = 12f)
        arcTo(2.2f, 2.2f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 14.2f, y1 = 12f)
        close()
    }
}.build()

/** An arrow pointing up, turned to point at a saved place. */
val Arrow: ImageVector = ImageVector.Builder(
    name = "Arrow",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(12f, 20f)
        lineTo(12f, 4.5f)
        moveTo(6f, 10.5f)
        lineTo(12f, 4.5f)
        lineTo(18f, 10.5f)
    }
}.build()

/** A plus, for stepping the map in. */
val ZoomIn: ImageVector = ImageVector.Builder(
    name = "ZoomIn",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round) {
        moveTo(12f, 5f)
        lineTo(12f, 19f)
        moveTo(5f, 12f)
        lineTo(19f, 12f)
    }
}.build()

/** A minus, for stepping the map out. */
val ZoomOut: ImageVector = ImageVector.Builder(
    name = "ZoomOut",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round) {
        moveTo(5f, 12f)
        lineTo(19f, 12f)
    }
}.build()

/** A card with a cross on it, for the emergency card. */
val EmergencyCard: ImageVector = ImageVector.Builder(
    name = "EmergencyCard",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(5f, 5.5f)
        lineTo(19f, 5.5f)
        lineTo(19f, 18.5f)
        lineTo(5f, 18.5f)
        close()
        moveTo(12f, 9f)
        lineTo(12f, 15f)
        moveTo(9f, 12f)
        lineTo(15f, 12f)
    }
}.build()

/** A bookmark, for keeping a place. */
val Bookmark: ImageVector = ImageVector.Builder(
    name = "Bookmark",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(6.5f, 3.5f)
        lineTo(17.5f, 3.5f)
        lineTo(17.5f, 20.5f)
        lineTo(12f, 16.5f)
        lineTo(6.5f, 20.5f)
        close()
    }
}.build()

/** The same bookmark, filled in: this place is kept. */
val Bookmarked: ImageVector = ImageVector.Builder(
    name = "Bookmarked",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = SolidColor(Color.Black),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(6.5f, 3.5f)
        lineTo(17.5f, 3.5f)
        lineTo(17.5f, 20.5f)
        lineTo(12f, 16.5f)
        lineTo(6.5f, 20.5f)
        close()
    }
}.build()

val Layers: ImageVector = ImageVector.Builder(
    name = "Layers",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(12f, 3.5f)
        lineTo(21f, 8.5f)
        lineTo(12f, 13.5f)
        lineTo(3f, 8.5f)
        close()
        moveTo(3f, 12.8f)
        lineTo(12f, 17.8f)
        lineTo(21f, 12.8f)
    }
}.build()

/**
 * An icon from SVG path data, stroked as the canvas draws its icons: 1.8 wide,
 * round caps and joins, on a 24-unit square. The paths below are the canvas's
 * own, so the app's icons are the design's and not a library's.
 */
private fun stroked(name: String, vararg paths: String): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    paths.forEach { data ->
        addPath(
            pathData = addPathNodes(data),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
}.build()

/** A speaker and its sound, for reading aloud. */
val Speak: ImageVector = stroked("Speak", "M4 9.5h3.5L12 5.5v13l-4.5-4H4z", "M15.5 9a4 4 0 0 1 0 6M18.2 6.3a7.8 7.8 0 0 1 0 11.4")

/** Three joined dots, for sharing. */
val ShareIcon: ImageVector = stroked(
    "Share",
    "M15 5.5a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0",
    "M4 12a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0",
    "M15 18.5a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0",
    "M8.7 10.8l6.6-4M8.7 13.2l6.6 4",
)

/** Two sheets, one over the other, for copying. */
val CopyIcon: ImageVector = stroked("Copy", "M10 8.5h8a1.5 1.5 0 0 1 1.5 1.5v8a1.5 1.5 0 0 1 -1.5 1.5h-8a1.5 1.5 0 0 1 -1.5 -1.5v-8a1.5 1.5 0 0 1 1.5 -1.5z", "M5 15.5V5.5a1 1 0 0 1 1-1h9.5")

/** Three finder squares and a scatter of modules, for a QR code. */
val QrIcon: ImageVector = stroked("QR code", "M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4z", "M14 14h2.5v2.5H14zM18 14h2M14 18.5h2M18.5 17.5V20h-2")

/** A chevron pointing down, turned up when a section is open. */
val Chevron: ImageVector = stroked("Chevron", "M6 9l6 6 6-6")

/** A folded map, for the map's tab. */
val MapIcon: ImageVector = stroked("Map", "M3.5 6.5l5.5-2.5 6 2.5 5.5-2.5v13.5l-5.5 2.5-6-2.5-5.5 2.5z", "M9 4v13.5M15 6.5V20")

/** Two sliders, for the settings tab. */
val SettingsIcon: ImageVector = stroked(
    "Settings",
    "M4 7h9M17 7h3M4 17h3M11 17h9",
    "M13 7a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
    "M7 17a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
)
