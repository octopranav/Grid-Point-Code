package com.gridpointcode.wear

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.ui.graphics.toArgb
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.ColorScheme
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.types.LayoutColor
import androidx.wear.protolayout.types.LayoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import ca.pranavpatel.algo.gridpointcode.design.brassDark
import ca.pranavpatel.algo.gridpointcode.design.groundDark
import ca.pranavpatel.algo.gridpointcode.design.inkDark
import ca.pranavpatel.algo.gridpointcode.design.inkMidDark
import ca.pranavpatel.algo.gridpointcode.design.surfaceDark
import com.google.common.util.concurrent.ListenableFuture
import com.gridpointcode.core.formatted
import java.util.Date

/**
 * The tile, from the canvas: the code the app last found, when, and how far to
 * trust it, with Read aloud at its foot.
 *
 * It never asks where the watch is. It shows the last fix for an hour and then,
 * without being asked again, offers to open the app instead, from the second
 * entry of its timeline. Read aloud opens the app, which reads the next place
 * it finds: where the wrist is now, not the code on the tile.
 */
class CodeTile : TileService() {

    override fun onTileRequest(request: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val device = request.deviceConfiguration
        val last = LastFixStore(this).read()
        val now = System.currentTimeMillis()
        val timeline = TimelineBuilders.Timeline.Builder()
        if (last != null && last.shown(now)) {
            timeline.addTimelineEntry(entry(found(this, device, last), end = last.shownUntil))
            timeline.addTimelineEntry(entry(open(this, device), start = last.shownUntil))
        } else {
            timeline.addTimelineEntry(entry(open(this, device)))
        }
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES)
            .setTileTimeline(timeline.build())
            .build()
        return CallbackToFutureAdapter.getFuture { it.set(tile) }
    }

    /** No images: the tile is text and a button. */
    override fun onTileResourcesRequest(request: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val resources = ResourceBuilders.Resources.Builder().setVersion(RESOURCES).build()
        return CallbackToFutureAdapter.getFuture { it.set(resources) }
    }

    private companion object {
        const val RESOURCES = "1"
    }
}

/** The palette's night colours, as the app's screens wear them; not the watch's own theme. */
private val TileColours = ColorScheme(
    primary = LayoutColor(brassDark.toArgb()),
    onPrimary = LayoutColor(groundDark.toArgb()),
    surfaceContainer = LayoutColor(surfaceDark.toArgb()),
    onSurface = LayoutColor(inkDark.toArgb()),
    onSurfaceVariant = LayoutColor(inkMidDark.toArgb()),
    background = LayoutColor(android.graphics.Color.BLACK),
    onBackground = LayoutColor(inkDark.toArgb()),
)

/**
 * One entry of the timeline, shown from [start] until [end], or always with
 * neither. An interval left without an end ends at zero, not never, and the
 * watch then finds no entry to show and draws the tile empty; so an open end
 * is the end of time, and an entry for always has no interval at all.
 */
private fun entry(layout: LayoutElement, start: Long? = null, end: Long? = null): TimelineBuilders.TimelineEntry {
    val entry = TimelineBuilders.TimelineEntry.Builder().setLayout(LayoutElementBuilders.Layout.fromLayoutElement(layout))
    if (start != null || end != null) {
        entry.setValidity(
            TimelineBuilders.TimeInterval.Builder()
                .setStartMillis(start ?: 0L)
                .setEndMillis(end ?: Long.MAX_VALUE)
                .build(),
        )
    }
    return entry.build()
}

private fun found(context: Context, device: DeviceParameters, last: LastFix): LayoutElement =
    materialScope(context, device, allowDynamicTheme = false, defaultColorScheme = TileColours) {
        val written = formatted(last.code)
        primaryLayout(
            titleSlot = { text(LayoutString(context.getString(R.string.tile_title))) },
            mainSlot = {
                LayoutElementBuilders.Column.Builder()
                    .addContent(text(LayoutString(written.substring(0, 6)), typography = Typography.DISPLAY_SMALL, color = colorScheme.primary))
                    .addContent(text(LayoutString(written.substring(7)), typography = Typography.DISPLAY_SMALL, color = colorScheme.primary))
                    .addContent(
                        text(
                            LayoutString(context.getString(R.string.tile_found, DateFormat.getTimeFormat(context).format(Date(last.at)), accuracyText(context, last.metres))),
                            typography = Typography.BODY_SMALL,
                            color = colorScheme.onSurfaceVariant,
                        ),
                    )
                    .build()
            },
            bottomSlot = {
                textEdgeButton(onClick = launch(context, read = true)) { text(LayoutString(context.getString(R.string.read_aloud))) }
            },
            onClick = launch(context, read = false),
        )
    }

private fun open(context: Context, device: DeviceParameters): LayoutElement =
    materialScope(context, device, allowDynamicTheme = false, defaultColorScheme = TileColours) {
        primaryLayout(
            titleSlot = { text(LayoutString(context.getString(R.string.tile_title))) },
            mainSlot = { text(LayoutString(context.getString(R.string.tile_open_note)), maxLines = 3, color = colorScheme.onSurfaceVariant) },
            bottomSlot = {
                textEdgeButton(onClick = launch(context, read = false)) { text(LayoutString(context.getString(R.string.tile_open))) }
            },
            onClick = launch(context, read = false),
        )
    }

/** Opens the app; with [read], it reads aloud the next place it finds. */
private fun launch(context: Context, read: Boolean): ModifiersBuilders.Clickable {
    val activity = ActionBuilders.AndroidActivity.Builder()
        .setPackageName(context.packageName)
        .setClassName(WatchActivity::class.java.name)
    if (read) activity.addKeyToExtraMapping(WatchActivity.READ, ActionBuilders.AndroidBooleanExtra.Builder().setValue(true).build())
    return ModifiersBuilders.Clickable.Builder()
        .setId(if (read) "read" else "open")
        .setOnClick(ActionBuilders.LaunchAction.Builder().setAndroidActivity(activity.build()).build())
        .build()
}
