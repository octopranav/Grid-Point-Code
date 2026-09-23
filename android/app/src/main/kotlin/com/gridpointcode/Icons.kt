package com.gridpointcode

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
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

/** Two stacked sheets, for choosing what the map is drawn from. */
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
