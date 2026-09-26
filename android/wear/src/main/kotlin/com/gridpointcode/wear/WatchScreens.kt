package com.gridpointcode.wear

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import ca.pranavpatel.algo.gridpointcode.GPC
import ca.pranavpatel.algo.gridpointcode.design.CodeStyle
import ca.pranavpatel.algo.gridpointcode.design.brassDark
import ca.pranavpatel.algo.gridpointcode.design.groundDark
import ca.pranavpatel.algo.gridpointcode.design.inkDark
import ca.pranavpatel.algo.gridpointcode.design.inkMidDark
import ca.pranavpatel.algo.gridpointcode.design.inkSoftDark
import ca.pranavpatel.algo.gridpointcode.design.level10Dark
import ca.pranavpatel.algo.gridpointcode.design.level10InkDark
import ca.pranavpatel.algo.gridpointcode.design.level1Dark
import ca.pranavpatel.algo.gridpointcode.design.level1InkDark
import ca.pranavpatel.algo.gridpointcode.design.level2Dark
import ca.pranavpatel.algo.gridpointcode.design.level2InkDark
import ca.pranavpatel.algo.gridpointcode.design.level3Dark
import ca.pranavpatel.algo.gridpointcode.design.level3InkDark
import ca.pranavpatel.algo.gridpointcode.design.level4Dark
import ca.pranavpatel.algo.gridpointcode.design.level4InkDark
import ca.pranavpatel.algo.gridpointcode.design.level5Dark
import ca.pranavpatel.algo.gridpointcode.design.level5InkDark
import ca.pranavpatel.algo.gridpointcode.design.level6Dark
import ca.pranavpatel.algo.gridpointcode.design.level6InkDark
import ca.pranavpatel.algo.gridpointcode.design.level7Dark
import ca.pranavpatel.algo.gridpointcode.design.level7InkDark
import ca.pranavpatel.algo.gridpointcode.design.level8Dark
import ca.pranavpatel.algo.gridpointcode.design.level8InkDark
import ca.pranavpatel.algo.gridpointcode.design.level9Dark
import ca.pranavpatel.algo.gridpointcode.design.level9InkDark
import ca.pranavpatel.algo.gridpointcode.design.surfaceDark
import com.gridpointcode.core.SavedOrder
import com.gridpointcode.core.SavedPlace
import com.gridpointcode.core.aloud
import com.gridpointcode.core.formatted
import com.gridpointcode.core.headingBetween
import com.gridpointcode.core.metresBetween
import com.gridpointcode.core.savedAt
import com.gridpointcode.core.savedRows
import com.gridpointcode.core.selectionOf
import com.gridpointcode.core.spellingFor
import com.gridpointcode.core.Source
import java.util.Locale

/*
 * The watch's three screens, from the canvas: where the watch is, the saved
 * places nearest first, and walking to one of them. Dark always, as a watch
 * face is, in the palette's night colours.
 */

private val WatchColours = ColorScheme(
    primary = brassDark,
    onPrimary = groundDark,
    surfaceContainer = surfaceDark,
    onSurface = inkDark,
    onSurfaceVariant = inkMidDark,
    background = Color.Black,
    onBackground = inkDark,
)

private val LEVELS = listOf(level1Dark, level2Dark, level3Dark, level4Dark, level5Dark, level6Dark, level7Dark, level8Dark, level9Dark, level10Dark)
private val INKS = listOf(
    level1InkDark, level2InkDark, level3InkDark, level4InkDark, level5InkDark,
    level6InkDark, level7InkDark, level8InkDark, level9InkDark, level10InkDark,
)

private const val HERE_ROUTE = "here"
private const val SAVED_ROUTE = "saved"
private const val WALK_ROUTE = "walk"

@Composable
fun WatchApp(model: WatchModel, speaker: WatchSpeaker, onAllow: () -> Unit) {
    MaterialTheme(colorScheme = WatchColours) {
        AppScaffold {
            val navigation = rememberSwipeDismissableNavController()
            SwipeDismissableNavHost(navController = navigation, startDestination = HERE_ROUTE) {
                composable(HERE_ROUTE) { Here(model, speaker, onAllow, onSaved = { navigation.navigate(SAVED_ROUTE) }) }
                composable(SAVED_ROUTE) { Saved(model, onOpen = { navigation.navigate("$WALK_ROUTE/$it") }) }
                composable("$WALK_ROUTE/{code}") { entry -> Walk(model, speaker, entry.arguments?.getString("code").orEmpty()) }
            }
        }
    }
}

/** Where the watch is: its code in two rows of cells, how far to trust it, and saying it or saving it. */
@Composable
private fun Here(model: WatchModel, speaker: WatchSpeaker, onAllow: () -> Unit, onSaved: () -> Unit) {
    val fix by model.fix.collectAsState()
    val saved by model.shelf.saved.collectAsState()
    val permitted by model.permitted.collectAsState()
    val context = LocalContext.current
    val noVoice = stringResource(R.string.no_voice)
    val list = rememberScalingLazyListState()
    ScreenScaffold(scrollState = list) {
        ScalingLazyColumn(state = list, modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            item { Eyebrow(stringResource(R.string.here)) }
            val here = fix
            when {
                !permitted -> {
                    item { Quiet(stringResource(R.string.allow_note)) }
                    item { Button(onClick = onAllow, label = { Text(stringResource(R.string.allow)) }) }
                }
                here == null -> item { Quiet(stringResource(R.string.finding)) }
                else -> {
                    val code = here.code
                    item { Mark(code) }
                    item {
                        Quiet(
                            if (here.metres <= INSIDE_ONE_CELL) stringResource(R.string.accuracy_inside)
                            else stringResource(R.string.accuracy, here.metres),
                        )
                    }
                    item {
                        val kept = saved.savedAt(code) != null
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val spelling = spellingFor(Locale.getDefault())
                                    if (!speaker.say(aloud(code, spelling), spelling.locale)) {
                                        Toast.makeText(context, noVoice, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                label = { Text(stringResource(R.string.read_aloud)) },
                            )
                            Button(
                                onClick = model::saveHere,
                                enabled = !kept,
                                colors = ButtonDefaults.filledTonalButtonColors(),
                                label = { Text(stringResource(if (kept) R.string.saved_here else R.string.save)) },
                            )
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = onSaved,
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.saved_open)) },
                )
            }
        }
    }
}

/** The saved places, nearest first from where the watch is, each with an arrow the way to go. */
@Composable
private fun Saved(model: WatchModel, onOpen: (String) -> Unit) {
    val fix by model.fix.collectAsState()
    val saved by model.shelf.saved.collectAsState()
    val facing by model.heading.collectAsState()
    val rows = savedRows(saved, fix?.point, SavedOrder.NEAREST)
    val list = rememberScalingLazyListState()
    ScreenScaffold(scrollState = list) {
        ScalingLazyColumn(state = list, modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            item { ListHeader { Text(stringResource(R.string.saved_title)) } }
            if (rows.isEmpty()) item { Quiet(stringResource(R.string.saved_none)) }
            items(rows, key = { it.place.code }) { row ->
                Button(
                    onClick = { onOpen(row.place.code) },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth(),
                    icon = { row.heading?.let { Arrow((it - (facing ?: 0f).toDouble()).toFloat(), Modifier.size(20.dp)) } },
                    secondaryLabel = { row.metres?.let { Text(distance(it), style = CodeStyle.copy(fontSize = 12.sp)) } },
                    label = { Text(name(row.place), maxLines = 1) },
                )
            }
        }
    }
}

/** Walking to a saved place: a large arrow the way to go, how far, and its short form to say. */
@Composable
private fun Walk(model: WatchModel, speaker: WatchSpeaker, code: String) {
    val fix by model.fix.collectAsState()
    val saved by model.shelf.saved.collectAsState()
    val facing by model.heading.collectAsState()
    val place = saved.savedAt(code) ?: SavedPlace(code, "", "", 0)
    val there = runCatching { selectionOf(code, Source.SAVED).point }.getOrNull()
    val here = fix
    val context = LocalContext.current
    val noVoice = stringResource(R.string.no_voice)
    val list = rememberScalingLazyListState()
    ScreenScaffold(scrollState = list) {
        ScalingLazyColumn(state = list, modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (here != null && there != null) {
                val way = headingBetween(here.point, there) - (facing ?: 0f)
                item { Arrow(way.toFloat(), Modifier.size(72.dp)) }
                item { Text(name(place), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold) }
                item { Text(distance(metresBetween(here.point, there)), style = CodeStyle.copy(fontSize = 28.sp, fontWeight = FontWeight.SemiBold)) }
            } else {
                item { Text(name(place), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold) }
                item { Quiet(stringResource(R.string.no_fix)) }
            }
            item {
                Text(
                    runCatching { GPC.Shorten(code) }.getOrDefault(formatted(code)),
                    style = CodeStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                    color = brassDark,
                )
            }
            item {
                Button(
                    onClick = {
                        val spelling = spellingFor(Locale.getDefault())
                        if (!speaker.say(aloud(code, spelling), spelling.locale)) {
                            Toast.makeText(context, noVoice, Toast.LENGTH_SHORT).show()
                        }
                    },
                    label = { Text(stringResource(R.string.speak)) },
                )
            }
        }
    }
}

/** The code in two rows of five cells, tinted by depth as on the phone, read aloud as its callouts. */
@Composable
private fun Mark(code: String) {
    val spoken = aloud(code, spellingFor(Locale.getDefault()))
    Column(
        modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
        verticalArrangement = Arrangement.spacedBy(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (row in 0 until 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (column in 0 until 5) {
                    val index = row * 5 + column
                    Box(
                        modifier = Modifier
                            .size(width = 26.dp, height = 32.dp)
                            .background(LEVELS[index], RoundedCornerShape(2.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            code.getOrNull(index)?.toString().orEmpty(),
                            style = CodeStyle.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                            color = INKS[index],
                        )
                    }
                }
            }
        }
    }
}

/** An arrow pointing [degrees] clockwise from straight up. */
@Composable
private fun Arrow(degrees: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.rotate(degrees)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.1f)
            lineTo(w * 0.8f, h * 0.9f)
            lineTo(w * 0.5f, h * 0.72f)
            lineTo(w * 0.2f, h * 0.9f)
            close()
        }
        drawPath(path, level1Dark)
    }
}

@Composable
private fun Eyebrow(text: String) {
    Text(text.uppercase(Locale.getDefault()), style = CodeStyle.copy(fontSize = 11.sp, letterSpacing = 1.5.sp), color = inkSoftDark)
}

@Composable
private fun Quiet(text: String) {
    Text(text, textAlign = TextAlign.Center, color = inkMidDark, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp))
}

/** A saved place's name, or its code when it was saved with none. */
private fun name(place: SavedPlace): String = place.label.ifEmpty { formatted(place.code) }

private fun distance(metres: Double): String =
    if (metres < 1000) String.format(Locale.getDefault(), "%d m", metres.toInt())
    else String.format(Locale.getDefault(), "%.1f km", metres / 1000)

/** A fix this tight names one cell, as the phone says it. */
private const val INSIDE_ONE_CELL = 2
