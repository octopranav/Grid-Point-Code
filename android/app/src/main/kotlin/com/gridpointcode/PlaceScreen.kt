package com.gridpointcode

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import com.gridpointcode.core.Locating
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.remember
import com.gridpointcode.map.Basemap
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.gridpointcode.core.AreaView
import com.gridpointcode.core.SPELLINGS
import com.gridpointcode.core.Spelling
import android.speech.tts.TextToSpeech
import android.widget.Toast
import com.gridpointcode.core.Emergency
import com.gridpointcode.core.emergencyOf
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import com.gridpointcode.core.Format
import com.gridpointcode.core.Origin
import com.gridpointcode.core.OtherFormat
import com.gridpointcode.core.SINGLE_CELL_METRES
import com.gridpointcode.core.SavedPlace
import com.gridpointcode.core.formatted
import com.gridpointcode.core.savedAt
import com.gridpointcode.core.selectionOf
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import com.gridpointcode.core.Anchor
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import com.gridpointcode.core.Named
import android.text.format.Formatter
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.ui.res.pluralStringResource
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.platform.LocalConfiguration
import com.gridpointcode.core.selectionAt
import com.gridpointcode.map.PlaceMap
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ca.pranavpatel.algo.gridpointcode.design.ButtonShape
import ca.pranavpatel.algo.gridpointcode.design.CodeMark
import ca.pranavpatel.algo.gridpointcode.design.CodeStyle
import ca.pranavpatel.algo.gridpointcode.design.LocalGpcColors
import ca.pranavpatel.algo.gridpointcode.design.Radius
import ca.pranavpatel.algo.gridpointcode.design.Space
import com.gridpointcode.core.Compass
import com.gridpointcode.core.Form
import com.gridpointcode.core.FormKey
import com.gridpointcode.core.LABEL_LIMIT
import com.gridpointcode.core.NOTE_LIMIT
import com.gridpointcode.core.PAD
import com.gridpointcode.core.PlaceView
import com.gridpointcode.core.Problem
import com.gridpointcode.core.Source
import java.util.Locale

/** Wider than this, the panel sits beside the map instead of over it. */
private const val WIDE_DP = 840

/** The panel's width beside the map, the same as the website's side panel. */
private const val PANE_DP = 420

/** How much of the panel shows over the map before it is pulled up: the code and its actions. */
private const val PEEK_DP = 260

/** The search bar's height over the map, with its margin. */
private const val SEARCH_DP = 96

/** The tallest the saved list gets before it scrolls. */
private const val SAVED_DP = 440

/** The tallest the list of places gets before it scrolls: four and a half rows, so it plainly does. */
private const val FOUND_DP = 270

/**
 * The place, on the map and in words.
 *
 * A phone shows the map with the panel as a sheet over it; a wide window puts
 * the panel beside it, laid out by the width of the window rather than by what
 * kind of device it is, so a tablet in a narrow split gets the phone layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceScreen(model: PlaceViewModel, speaker: Speaker) {
    val ui by model.ui.collectAsState()
    val basemap by model.basemap.collectAsState()
    val finding by model.found.collectAsState()
    val saved by model.saved.collectAsState()
    val carding by model.emergency.collectAsState()
    val anchoring by model.anchors.collectAsState()
    var browsing by remember { mutableStateOf(false) }
    val savedPoints = remember(saved) {
        saved.mapNotNull { place -> runCatching { selectionOf(place.code, Source.SAVED).point }.getOrNull() }
    }
    val wide = LocalConfiguration.current.screenWidthDp >= WIDE_DP
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + SEARCH_DP.dp
    val bottom = if (wide) WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() else PEEK_DP.dp

    val context = LocalContext.current
    // Every line is read in the listener's words and voice; with no voice for
    // their language the words are left on the screen to be read out instead.
    val ready by speaker.ready.collectAsState()
    val listener = ui.listener
    val voiced = remember(ready, listener) { speaker.voiced(listener.locale) }
    val noVoice = stringResource(R.string.aloud_no_voice_short, listener.name)
    val speak: (String) -> Unit = { line ->
        if (!speaker.say(line, listener.locale)) Toast.makeText(context, noVoice, Toast.LENGTH_SHORT).show()
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) model.locate() else model.refused()
    }
    // Precise and approximate are asked for together; Android lets the reader
    // choose, and an approximate fix is shown with the accuracy it admits to.
    val locate = {
        if (hasLocation(context)) model.locate()
        else ask.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    val map: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier) {
            PlaceMap(
                selection = ui.selection,
                onPick = { model.place(selectionAt(it, Source.MAP)) },
                padding = PaddingValues(top = top, bottom = bottom),
                basemap = basemap,
                saved = savedPoints,
                area = ui.area?.box,
                modifier = Modifier.fillMaxSize(),
            )
            Search(
                text = model.query,
                onType = model::type,
                onGo = model::go,
                finding = finding,
                onPick = model::pick,
                onRecall = model::recall,
                onInstead = model::openInstead,
                problem = ui.problem,
                onSettings = { openSettings(context) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(Space.step2),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = Space.step3, bottom = bottom + Space.step3),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(Space.step2),
            ) {
                EmergencyButton(onClick = model::openEmergency)
                SavedButton(onClick = { browsing = true })
                Basemaps(chosen = basemap, onChoose = model::choose)
                Locate(locating = ui.locating, onClick = locate)
            }
        }
    }

    // Opening the card asks the device where it is, afresh: the card is for now.
    LaunchedEffect(carding) {
        if (carding) locate()
    }
    if (carding) {
        EmergencyCardScreen(
            emergency = emergencyOf(ui),
            near = anchoring.chosen?.takeIf { anchoring.code == ui.selection.code }?.landmark
                ?.let { if (it.region.isEmpty()) it.name else it.name + ", " + it.region },
            onAllow = locate,
            onSettings = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
            speak = speak,
            onClose = model::closeEmergency,
        )
    }

    if (browsing) {
        SavedList(
            saved = saved,
            onOpen = { place ->
                browsing = false
                model.recall(place)
            },
            onDismiss = { browsing = false },
        )
    }

    if (wide) {
        Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            map(Modifier.weight(1f).fillMaxHeight())
            Panel(
                ui = ui,
                model = model,
                speak = speak,
                voiced = voiced,
                modifier = Modifier
                    .width(PANE_DP.dp)
                    .fillMaxHeight()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            )
        }
    } else {
        BottomSheetScaffold(
            sheetContent = {
                Panel(ui = ui, model = model, speak = speak, voiced = voiced, modifier = Modifier.navigationBarsPadding())
            },
            sheetPeekHeight = PEEK_DP.dp,
            sheetContainerColor = MaterialTheme.colorScheme.background,
            sheetShape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
        ) {
            map(Modifier.fillMaxSize())
        }
    }
}

/** The same groups the website's playground has, in the same order. */
@Composable
private fun Panel(
    ui: PlaceView,
    model: PlaceViewModel,
    speak: (String) -> Unit,
    voiced: Boolean?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val anchoring by model.anchors.collectAsState()
    val keeping by model.offline.collectAsState()
    val saved by model.saved.collectAsState()
    val here = saved.savedAt(ui.selection.code)
    var editing by remember { mutableStateOf(false) }
    if (editing) {
        SaveDialog(
            existing = here,
            note = ui.note,
            onSave = { label, note ->
                model.save(label, note)
                editing = false
            },
            onRemove = {
                model.unsave(ui.selection.code)
                editing = false
            },
            onDismiss = { editing = false },
        )
    }
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.step3, vertical = Space.step2),
        verticalArrangement = Arrangement.spacedBy(Space.step3),
    ) {
        val area = ui.area
        // An area shared by somebody is its own subject; one widened from a
        // place has the place underneath, to go back to and to widen again.
        val fromPlace = ui.selection.source != Source.AREA
        if (area != null) {
            AreaHead(
                area = area,
                onBack = if (fromPlace) model::narrow else null,
                speak = { speak(area.spoken) },
                share = { share(context, area.cell + "\n" + area.link) },
                copy = { copy(context, area.cell) },
            )
            if (fromPlace) ShareArea(ui.areas, chosen = area.level, onChoose = model::widen)
            Spacer(Modifier.height(Space.step5))
            return@Column
        }
        Head(
            ui = ui,
            saved = here,
            onBookmark = { editing = true },
            speak = { speak(ui.spoken) },
            share = { share(context, ui.formatted + "\n" + ui.link) },
            copy = { copy(context, ui.formatted) },
        )
        Nudge(ui.selection.code, ui.pad, onNudge = model::nudge)
        WrittenForms(ui.forms, copy = { copy(context, it) })
        GiveAddress(ui.note, ui.link, onNote = model::describeTheWay, share = { share(context, ui.formatted + "\n" + ui.link) })
        ShareArea(ui.areas, chosen = null, onChoose = model::widen)
        AnchorShort(
            anchoring = anchoring,
            keeping = keeping,
            onChoose = model::anchorTo,
            onKeep = model::keepArea,
            onForget = model::forgetKept,
            copy = { copy(context, it) },
            share = { share(context, it) },
        )
        Aloud(ui.spoken, ui.listener, voiced, onChoose = model::readTo, speak = { speak(ui.spoken) })
        Spacer(Modifier.height(Space.step5))
    }
}

/**
 * One field for everything: a code, a point, a link, or the name of a place,
 * which lists the places it could mean as it is typed.
 */
@Composable
private fun Search(
    text: String,
    onType: (String) -> Unit,
    onGo: (String) -> Unit,
    finding: Finding,
    onPick: (Named) -> Unit,
    onRecall: (SavedPlace) -> Unit,
    onInstead: (OtherFormat.At) -> Unit,
    problem: Problem?,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    // The map is what answers, so the keyboard gets out of its way.
    val go = {
        keyboard?.hide()
        onGo(text)
    }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(Radius.card),
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Space.step1), verticalArrangement = Arrangement.spacedBy(Space.step0)) {
            OutlinedTextField(
                value = text,
                onValueChange = onType,
                label = { Text(stringResource(R.string.search_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { go() }),
                trailingIcon = { TextButton(onClick = go) { Text(stringResource(R.string.search_go)) } },
                modifier = Modifier.fillMaxWidth(),
            )
            Found(
                finding = finding,
                onPick = { place ->
                    keyboard?.hide()
                    focus.clearFocus()
                    onPick(place)
                },
                onRecall = { place ->
                    keyboard?.hide()
                    focus.clearFocus()
                    onRecall(place)
                },
                onInstead = { at ->
                    keyboard?.hide()
                    focus.clearFocus()
                    onInstead(at)
                },
            )
            if (problem != null) {
                Text(
                    text = describe(problem),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = Space.step1, vertical = Space.step0),
                )
                if (problem == Problem.LocationRefused) {
                    TextButton(onClick = onSettings) { Text(stringResource(R.string.settings)) }
                }
            }
        }
    }
}

/** The places a name could mean, and why there are none when there are none. */
@Composable
private fun Found(
    finding: Finding,
    onPick: (Named) -> Unit,
    onRecall: (SavedPlace) -> Unit,
    onInstead: (OtherFormat.At) -> Unit,
) {
    val looking = stringResource(R.string.names_looking)
    if (finding.status == Finding.Status.LOOKING) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.step1)
                .semantics { contentDescription = looking },
        )
    }
    val note = when (finding.status) {
        Finding.Status.MISSING -> stringResource(R.string.names_missing, finding.query)
        Finding.Status.OFFLINE -> stringResource(R.string.names_offline)
        Finding.Status.LOCAL -> stringResource(R.string.names_local)
        else -> null
    }
    if (note != null) {
        Text(
            text = note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Space.step1, vertical = Space.step0),
        )
    }
    if (finding.places.isEmpty() && finding.saved.isEmpty() && finding.alternative == null) return
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = FOUND_DP.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        finding.alternative?.let { at ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onInstead(at) }
                    .padding(horizontal = Space.step2, vertical = Space.step1),
            ) {
                Text(stringResource(R.string.instead_digipin), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = String.format(Locale.getDefault(), "%.5f, %.5f", at.point.latitude, at.point.longitude),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        finding.saved.forEach { place ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onRecall(place) }
                    .padding(horizontal = Space.step2, vertical = Space.step1),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.step2),
            ) {
                Icon(Bookmarked, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                SavedLines(place)
            }
        }
        finding.places.forEach { place ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onPick(place) }
                    .padding(horizontal = Space.step2, vertical = Space.step1),
            ) {
                Text(place.name, style = MaterialTheme.typography.bodyLarge)
                if (place.region.isNotEmpty()) {
                    Text(
                        text = place.region,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun describe(problem: Problem): String = when (problem) {
    is Problem.Unread -> {
        val reason = problem.reason
        if (reason.isNullOrBlank()) stringResource(R.string.problem_unread)
        else stringResource(R.string.problem_unread_reason, reason)
    }
    is Problem.Reserved -> stringResource(R.string.problem_reserved, problem.code)
    is Problem.UnreadShort -> stringResource(R.string.problem_short, problem.short)
    is Problem.Closed -> stringResource(R.string.problem_closed_what3words)
    is Problem.Unfollowed -> stringResource(R.string.problem_unfollowed_google)
    is Problem.Unplaced -> stringResource(
        if (problem.why == Problem.Unanchored.Why.UNREACHABLE) R.string.problem_unplaced_unreachable
        else R.string.problem_unplaced_not_found,
        problem.code,
        problem.locality,
    )
    is Problem.Unanchored -> stringResource(
        when (problem.why) {
            Problem.Unanchored.Why.NOT_FOUND -> R.string.problem_anchor_not_found
            Problem.Unanchored.Why.SEVERAL -> R.string.problem_anchor_several
            Problem.Unanchored.Why.NOT_UNIQUE -> R.string.problem_anchor_not_unique
            Problem.Unanchored.Why.UNREACHABLE -> R.string.problem_anchor_unreachable
        },
        problem.short,
        problem.reference,
    )
    Problem.LocationRefused -> stringResource(R.string.problem_location_refused)
    Problem.LocationOff -> stringResource(R.string.problem_location_off)
    Problem.NoFix -> stringResource(R.string.problem_no_fix)
}

/** Where a converted place came from, by the name its owner gives it. */
@Composable
private fun fromLabel(format: Format): String = stringResource(
    when (format) {
        Format.PLUS_CODE -> R.string.from_plus_code
        Format.DIGIPIN -> R.string.from_digipin
        Format.GEOHASH -> R.string.from_geohash
        Format.GOOGLE_MAPS -> R.string.from_google_maps
        Format.APPLE_MAPS -> R.string.from_apple_maps
        Format.OPENSTREETMAP -> R.string.from_openstreetmap
        Format.BING_MAPS -> R.string.from_bing_maps
        Format.WAZE -> R.string.from_waze
        Format.WHAT3WORDS -> R.string.source_converted
    },
)

/**
 * How much the source said. A code names 2.5 m wherever it came from, and when
 * the source named more than a cell, or only the middle of a map view, the
 * reader is told rather than left to take the code for a door.
 */
@Composable
private fun originNote(origin: Origin): String? {
    val link = origin.format in setOf(Format.GOOGLE_MAPS, Format.APPLE_MAPS, Format.OPENSTREETMAP, Format.BING_MAPS, Format.WAZE)
    val coarse = origin.metres > SINGLE_CELL_METRES
    return when {
        origin.viewCentre -> stringResource(R.string.origin_view)
        link && coarse -> stringResource(R.string.origin_decimals, distance(origin.metres))
        link -> null
        coarse -> stringResource(R.string.origin_area, origin.text, distance(origin.metres))
        else -> stringResource(R.string.origin_read, origin.text)
    }
}

/** A level's name, from the specification's table of scales. */
@Composable
private fun levelName(level: Int): String = stringResource(
    when (level) {
        3 -> R.string.level_3
        4 -> R.string.level_4
        5 -> R.string.level_5
        6 -> R.string.level_6
        7 -> R.string.level_7
        8 -> R.string.level_8
        else -> R.string.level_9
    },
)

/**
 * The areas around the place, a region down to a building, each written as its
 * cell with its size where it lies. Choosing one shows it, to share instead of
 * the door: a market, a block, a delivery zone, in the same alphabet.
 */
@Composable
private fun ShareArea(areas: List<AreaView>, chosen: Int?, onChoose: (Int) -> Unit) {
    Section(stringResource(R.string.area_title)) {
        Quiet(stringResource(R.string.area_explain))
        Column(Modifier.selectableGroup()) {
            areas.forEach { area ->
                val selected = area.level == chosen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = selected, role = Role.RadioButton) { onChoose(area.level) }
                        .padding(vertical = Space.step1),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.step2),
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Column(Modifier.weight(1f)) {
                        Text(area.cell, style = CodeStyle)
                        Text(
                            text = stringResource(
                                R.string.area_row,
                                levelName(area.level),
                                distance(area.size.northSouthMetres),
                                distance(area.size.eastWestMetres),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * An area in place of the place: its cell, with no hash and no hyphen, because
 * it must never be read as a code; its size; and sharing it, which sends the
 * cell and a link the website opens as the same area.
 */
@Composable
private fun AreaHead(
    area: AreaView,
    onBack: (() -> Unit)?,
    speak: () -> Unit,
    share: () -> Unit,
    copy: () -> Unit,
) {
    val colours = LocalGpcColors.current
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(Radius.card),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colours.rule, RoundedCornerShape(Radius.card)),
    ) {
        Column(Modifier.padding(Space.step3), verticalArrangement = Arrangement.spacedBy(Space.step2)) {
            Text(
                text = stringResource(R.string.area_label, area.level, levelName(area.level)).uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall,
                color = colours.inkSoft,
            )
            Text(area.cell, style = CodeStyle.copy(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold))
            Text(
                text = stringResource(R.string.area_size, distance(area.size.northSouthMetres), distance(area.size.eastWestMetres)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Quiet(stringResource(R.string.area_note, area.cell))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.step1)) {
                Button(onClick = speak, shape = ButtonShape) { Text(stringResource(R.string.read_aloud)) }
                FilledTonalButton(onClick = share, shape = ButtonShape) { Text(stringResource(R.string.share)) }
                OutlinedButton(onClick = copy, shape = ButtonShape) { Text(stringResource(R.string.copy)) }
            }
            if (onBack != null) TextButton(onClick = onBack) { Text(stringResource(R.string.area_back)) }
        }
    }
}

/** The button that opens the emergency card, in the design's crimson. */
@Composable
private fun EmergencyButton(onClick: () -> Unit) {
    val label = stringResource(R.string.emergency_open)
    SmallFloatingActionButton(
        onClick = onClick,
        shape = ButtonShape,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { contentDescription = label },
    ) {
        Icon(EmergencyCard, contentDescription = null)
    }
}

/**
 * Where the reader is, in large type, for reading to somebody who has never
 * heard of this format: the plain coordinates first, since any operator can use
 * them, then the code and how to say it, and how far to trust the fix. It keeps
 * the screen awake while it is up, and needs no connection.
 */
@Composable
private fun EmergencyCardScreen(
    emergency: Emergency,
    near: String?,
    onAllow: () -> Unit,
    onSettings: () -> Unit,
    speak: (String) -> Unit,
    onClose: () -> Unit,
) {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    // Edge to edge, so nothing of the screen behind shows around it; the card
    // keeps itself clear of the system bars.
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // The card's own window, drawn under the system bars, sets their icons
        // dark on a light card and light on a dark one, so the clock stays legible.
        val card = LocalView.current
        val light = MaterialTheme.colorScheme.background.luminance() > 0.5f
        SideEffect {
            (card.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.getInsetsController(window, card).apply {
                    isAppearanceLightStatusBars = light
                    isAppearanceLightNavigationBars = light
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = Space.step4, vertical = Space.step3),
                verticalArrangement = Arrangement.spacedBy(Space.step3),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.emergency_title),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClose) { Text(stringResource(R.string.emergency_close)) }
                }
                Text(stringResource(R.string.emergency_note), style = MaterialTheme.typography.bodyLarge)
                when (emergency) {
                    Emergency.Finding -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.step2),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.locating_seeking), style = MaterialTheme.typography.titleMedium)
                    }
                    is Emergency.Here -> EmergencyHere(emergency, near, speak)
                    Emergency.Refused -> {
                        Text(stringResource(R.string.emergency_refused), style = MaterialTheme.typography.titleMedium)
                        Button(onClick = onAllow, shape = ButtonShape) { Text(stringResource(R.string.emergency_allow)) }
                    }
                    Emergency.Off -> {
                        Text(stringResource(R.string.emergency_off), style = MaterialTheme.typography.titleMedium)
                        Button(onClick = onSettings, shape = ButtonShape) { Text(stringResource(R.string.settings)) }
                    }
                    Emergency.NoFix -> {
                        Text(stringResource(R.string.problem_no_fix), style = MaterialTheme.typography.titleMedium)
                        Button(onClick = onAllow, shape = ButtonShape) { Text(stringResource(R.string.emergency_again)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmergencyHere(here: Emergency.Here, near: String?, speak: (String) -> Unit) {
    val large = CodeStyle.copy(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold)
    val said = here.said
    Column(verticalArrangement = Arrangement.spacedBy(Space.step1)) {
        Text(stringResource(R.string.emergency_coordinates), style = MaterialTheme.typography.labelLarge)
        Text(here.decimal, style = large)
        Text(here.degrees, style = CodeStyle.copy(fontSize = 20.sp, lineHeight = 26.sp))
    }
    Column(verticalArrangement = Arrangement.spacedBy(Space.step1)) {
        Text(stringResource(R.string.emergency_code), style = MaterialTheme.typography.labelLarge)
        Text(here.formatted, style = large)
        Text(here.spoken, style = MaterialTheme.typography.titleMedium)
        if (near != null) {
            Text(stringResource(R.string.emergency_near, near), style = MaterialTheme.typography.titleMedium)
        }
    }
    Text(
        text = stringResource(
            if (here.insideOneCell) R.string.emergency_inside else R.string.emergency_accuracy,
            here.metres,
        ) + if (here.refining) " " + stringResource(R.string.locating_refining) else "",
        style = MaterialTheme.typography.titleMedium,
    )
    Button(onClick = { speak(said) }, shape = ButtonShape, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Text(stringResource(R.string.read_aloud), style = MaterialTheme.typography.titleMedium)
    }
}

/** The button that opens the saved places. */
@Composable
private fun SavedButton(onClick: () -> Unit) {
    val label = stringResource(R.string.saved_title)
    SmallFloatingActionButton(
        onClick = onClick,
        shape = ButtonShape,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { contentDescription = label },
    ) {
        Icon(Bookmark, contentDescription = null)
    }
}

/** A saved place's name, or its code when it has none, with the code and the directions under it. */
@Composable
private fun SavedLines(place: SavedPlace) {
    Column {
        Text(place.label.ifEmpty { formatted(place.code) }, style = MaterialTheme.typography.bodyLarge)
        val under = listOfNotNull(formatted(place.code).takeIf { place.label.isNotEmpty() }, place.note.ifEmpty { null })
        if (under.isNotEmpty()) {
            Text(
                text = under.joinToString(" \u00b7 "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

/** Every saved place, most recent first. Opening one goes there with its directions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedList(saved: List<SavedPlace>, onOpen: (SavedPlace) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .padding(horizontal = Space.step3)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Space.step2),
        ) {
            Text(stringResource(R.string.saved_title), style = MaterialTheme.typography.titleMedium)
            if (saved.isEmpty()) Quiet(stringResource(R.string.saved_empty))
            LazyColumn(Modifier.heightIn(max = SAVED_DP.dp)) {
                items(saved, key = { it.code }) { place ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { onOpen(place) }
                            .padding(vertical = Space.step2),
                    ) {
                        SavedLines(place)
                    }
                    HorizontalDivider(color = LocalGpcColors.current.rule)
                }
            }
            Spacer(Modifier.height(Space.step3))
        }
    }
}

/**
 * Saving the place on screen, or changing one already saved: a name and the
 * directions to the door, both optional. Removing is here too, where it is a
 * deliberate choice rather than a slip of the thumb.
 */
@Composable
private fun SaveDialog(
    existing: SavedPlace?,
    note: String,
    onSave: (String, String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember { mutableStateOf(existing?.label.orEmpty()) }
    var directions by remember { mutableStateOf(existing?.note ?: note) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.save_place else R.string.saved_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.step2)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(LABEL_LIMIT) },
                    label = { Text(stringResource(R.string.save_label)) },
                    placeholder = { Text(stringResource(R.string.save_label_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = directions,
                    onValueChange = { directions = it.take(NOTE_LIMIT) },
                    label = { Text(stringResource(R.string.save_note)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(label, directions) }) { Text(stringResource(R.string.save_confirm)) }
        },
        dismissButton = {
            Row {
                if (existing != null) {
                    TextButton(onClick = onRemove) {
                        Text(stringResource(R.string.save_remove), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.save_cancel)) }
            }
        },
    )
}

/** The six basemaps the website offers, the chosen one marked. */
@Composable
private fun Basemaps(chosen: Basemap, onChoose: (Basemap) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.basemap)
    Box {
        SmallFloatingActionButton(
            onClick = { open = true },
            shape = ButtonShape,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { contentDescription = label },
        ) {
            Icon(Layers, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Basemap.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(basemapName(option))) },
                    leadingIcon = { RadioButton(selected = option == chosen, onClick = null) },
                    onClick = {
                        onChoose(option)
                        open = false
                    },
                )
            }
        }
    }
}

private fun basemapName(basemap: Basemap): Int = when (basemap) {
    Basemap.AUTO -> R.string.basemap_auto
    Basemap.POSITRON -> R.string.basemap_positron
    Basemap.BRIGHT -> R.string.basemap_bright
    Basemap.LIBERTY -> R.string.basemap_liberty
    Basemap.DARK -> R.string.basemap_dark
    Basemap.FIORD -> R.string.basemap_fiord
}

/** The locate button. It shows that it is waiting while no fix has come yet. */
@Composable
private fun Locate(locating: Locating, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.locate)
    FloatingActionButton(
        onClick = onClick,
        shape = ButtonShape,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier.semantics { contentDescription = label },
    ) {
        if (locating == Locating.SEEKING) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(Crosshair, contentDescription = null)
        }
    }
}

@Composable
private fun Head(
    ui: PlaceView,
    saved: SavedPlace?,
    onBookmark: () -> Unit,
    speak: () -> Unit,
    share: () -> Unit,
    copy: () -> Unit,
) {
    val colours = LocalGpcColors.current
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(Radius.card),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colours.rule, RoundedCornerShape(Radius.card)),
    ) {
        Column(Modifier.padding(Space.step3), verticalArrangement = Arrangement.spacedBy(Space.step2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = (ui.origin?.let { fromLabel(it.format) } ?: sourceLabel(ui.selection.source))
                            .uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        color = colours.inkSoft,
                    )
                    // A saved place is shown by its name wherever it is reached from,
                    // a tap on the map as much as the list.
                    if (saved != null && saved.label.isNotEmpty()) {
                        Text(saved.label, style = MaterialTheme.typography.titleMedium)
                    }
                }
                val describe = stringResource(if (saved == null) R.string.save_place else R.string.saved_edit)
                IconButton(onClick = onBookmark, modifier = Modifier.semantics { contentDescription = describe }) {
                    Icon(
                        if (saved == null) Bookmark else Bookmarked,
                        contentDescription = null,
                        tint = if (saved == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    )
                }
            }
            CodeMark(code = ui.selection.code, spoken = ui.spoken)
            Text(
                text = stringResource(R.string.cell_size, metres(ui.cell.northSouthMetres), metres(ui.cell.eastWestMetres)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ui.origin?.let { origin ->
                originNote(origin)?.let { note ->
                    Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when (ui.locating) {
                Locating.SEEKING -> Text(
                    stringResource(R.string.locating_seeking),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Locating.REFINING -> Text(
                    stringResource(R.string.locating_refining),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Locating.IDLE -> Unit
            }
            ui.fix?.let { fix ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.step1)) {
                    if (!fix.insideOneCell) {
                        Box(Modifier.size(width = 14.dp, height = 3.dp).background(colours.code))
                    }
                    Text(
                        text = if (fix.insideOneCell) stringResource(R.string.fix_inside, fix.metres)
                        else stringResource(R.string.fix_wider, fix.metres),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.step1)) {
                Button(onClick = speak, shape = ButtonShape) { Text(stringResource(R.string.read_aloud)) }
                FilledTonalButton(onClick = share, shape = ButtonShape) { Text(stringResource(R.string.share)) }
                OutlinedButton(onClick = copy, shape = ButtonShape) { Text(stringResource(R.string.copy)) }
            }
        }
    }
}

@Composable
private fun sourceLabel(source: Source): String = stringResource(
    when (source) {
        Source.SAMPLE -> R.string.source_sample
        Source.DEVICE -> R.string.source_device
        Source.CODE -> R.string.source_code
        Source.LINK -> R.string.source_link
        Source.NUDGE -> R.string.source_nudge
        Source.MAP -> R.string.source_map
        Source.SEARCH -> R.string.source_search
        Source.ANCHORED -> R.string.source_anchored
        Source.SAVED -> R.string.source_saved
        Source.CONVERTED -> R.string.source_converted
        Source.AREA -> R.string.source_area
    },
)

/** How many landmarks show before the reader asks for the rest. */
private const val ANCHORS_SHOWN = 5

/**
 * The places near enough to give the short form with, and the line that gives it.
 *
 * Every one listed is inside the recovery box, so each is a safe choice, and the
 * nearest is chosen to begin with. Anything further off is left out rather than
 * listed lower: it would not fail, it would name somewhere else.
 */
@Composable
private fun AnchorShort(
    anchoring: Anchoring,
    keeping: Keeping,
    onChoose: (Anchor) -> Unit,
    onKeep: () -> Unit,
    onForget: () -> Unit,
    copy: (String) -> Unit,
    share: (String) -> Unit,
) {
    var all by remember(anchoring.code) { mutableStateOf(false) }
    val colours = LocalGpcColors.current
    Section(stringResource(R.string.anchor_title)) {
        Quiet(stringResource(R.string.anchor_explain))
        when (anchoring.status) {
            Anchoring.Status.LOOKING -> Quiet(stringResource(R.string.anchor_looking))
            Anchoring.Status.NONE -> Quiet(stringResource(R.string.anchor_none))
            Anchoring.Status.UNREACHABLE -> Quiet(stringResource(R.string.anchor_unreachable))
            Anchoring.Status.FOUND -> Unit
        }
        val shown = if (all) anchoring.anchors else anchoring.anchors.take(ANCHORS_SHOWN)
        Column(Modifier.selectableGroup()) {
            shown.forEach { anchor ->
                val selected = anchor == anchoring.chosen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = selected, role = Role.RadioButton) { onChoose(anchor) }
                        .padding(vertical = Space.step1),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.step2),
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Column(Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.step1), verticalAlignment = Alignment.CenterVertically) {
                            Text(anchor.landmark.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false))
                            if (anchor.exact) {
                                Text(stringResource(R.string.anchor_exact), style = MaterialTheme.typography.labelSmall, color = colours.code)
                            }
                        }
                        Text(
                            text = stringResource(R.string.anchor_where, distance(anchor.metres), anchor.bearing, anchor.landmark.region),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (!all && anchoring.anchors.size > ANCHORS_SHOWN) {
            TextButton(onClick = { all = true }) {
                Text(stringResource(R.string.anchor_show_all, anchoring.anchors.size))
            }
        }
        if (anchoring.partial) Quiet(stringResource(R.string.anchor_partial))
        if (shown.any { it.exact }) Quiet(stringResource(R.string.anchor_exact_note))
        anchoring.line?.let { line ->
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(Radius.card),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colours.rule, RoundedCornerShape(Radius.card)),
            ) {
                Text(line, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(Space.step2))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.step1)) {
                FilledTonalButton(onClick = { share(line) }, shape = ButtonShape) { Text(stringResource(R.string.share)) }
                OutlinedButton(onClick = { copy(line) }, shape = ButtonShape) { Text(stringResource(R.string.copy)) }
            }
        }
        Offline(keeping, onKeep, onForget)
    }
}

/**
 * Keeping the area around the place, so its landmarks work with no connection.
 * Offered only once the archive is known to be there: a button that can only
 * fail is no better than one that silently does nothing.
 */
@Composable
private fun Offline(keeping: Keeping, onKeep: () -> Unit, onForget: () -> Unit) {
    if (!keeping.known) return
    val context = LocalContext.current
    val here = keeping.area
    if (!keeping.covered || here == null) {
        OutlinedButton(onClick = onKeep, enabled = !keeping.busy, shape = ButtonShape) {
            Text(stringResource(if (keeping.busy) R.string.keep_busy else R.string.keep_area))
        }
        Quiet(stringResource(R.string.keep_explain, keeping.northSouthKm, keeping.eastWestKm))
    } else if (here.held == 0) {
        Quiet(stringResource(R.string.keep_empty))
    } else {
        Quiet(
            pluralStringResource(
                R.plurals.keep_kept,
                here.held,
                here.held,
                Formatter.formatShortFileSize(context, here.bytes),
                DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(here.keptOn),
            ),
        )
    }
    Quiet(stringResource(R.string.keep_map))
    when (keeping.note) {
        Keeping.Note.FAILED -> Quiet(stringResource(R.string.keep_failed))
        Keeping.Note.FORGOTTEN -> Quiet(stringResource(R.string.keep_forgotten))
        null -> Unit
    }
    if (keeping.areas > 0) {
        TextButton(onClick = onForget) {
            Text(
                pluralStringResource(
                    R.plurals.keep_forget,
                    keeping.areas,
                    keeping.areas,
                    Formatter.formatShortFileSize(context, keeping.bytes),
                ),
            )
        }
    }
}

/** A note in the quieter ink. */
@Composable
private fun Quiet(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Metres under a kilometre, tenths of a kilometre above. */
@Composable
private fun distance(metres: Double): String =
    if (metres < 1000) stringResource(R.string.distance_metres, metres.roundToInt())
    else stringResource(R.string.distance_kilometres, metres / 1000)

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.step2)) {
        HorizontalDivider(color = LocalGpcColors.current.rule)
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun Nudge(code: String, pad: Map<Compass, String>, onNudge: (Compass) -> Unit) {
    Section(stringResource(R.string.nudge_title)) {
        if (pad.isEmpty()) {
            Text(stringResource(R.string.nudge_pole), style = MaterialTheme.typography.bodySmall)
            return@Section
        }
        Column(verticalArrangement = Arrangement.spacedBy(Space.step0)) {
            PAD.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Space.step0)) {
                    row.forEach { direction ->
                        val modifier = Modifier.weight(1f).height(58.dp)
                        if (direction == null) {
                            PadCell(label = stringResource(R.string.nudge_here), tail = code.takeLast(5), current = true, modifier = modifier)
                        } else {
                            OutlinedButton(
                                onClick = { onNudge(direction) },
                                shape = ButtonShape,
                                modifier = modifier,
                            ) {
                                PadCell(label = direction.name, tail = pad.getValue(direction).takeLast(5), current = false)
                            }
                        }
                    }
                }
            }
        }
        Text(stringResource(R.string.nudge_note), style = MaterialTheme.typography.bodySmall, color = LocalGpcColors.current.inkSoft)
    }
}

@Composable
private fun PadCell(label: String, tail: String, current: Boolean, modifier: Modifier = Modifier) {
    val colours = LocalGpcColors.current
    Column(
        modifier.then(
            if (current) Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.card))
                .border(1.dp, colours.code, RoundedCornerShape(Radius.card))
            else Modifier,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colours.inkSoft)
        Text("-$tail", style = CodeStyle, color = if (current) colours.code else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun WrittenForms(forms: List<Form>, copy: (String) -> Unit) {
    val colours = LocalGpcColors.current
    Section(stringResource(R.string.forms_title)) {
        forms.forEach { form ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(formLabel(form.key), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        form.value,
                        style = CodeStyle,
                        color = if (form.key == FormKey.SHORT || form.key == FormKey.CHECK) colours.code else MaterialTheme.colorScheme.onSurface,
                    )
                }
                TextButton(onClick = { copy(form.value) }) { Text(stringResource(R.string.copy)) }
            }
        }
    }
}

@Composable
private fun formLabel(key: FormKey): String = when (key) {
    FormKey.SHORT -> stringResource(R.string.form_short) + " · " + stringResource(R.string.form_short_caution)
    FormKey.CHECK -> stringResource(R.string.form_check) + " · " + stringResource(R.string.form_check_caution)
    FormKey.INTEGER -> stringResource(R.string.form_integer) + " · " + stringResource(R.string.form_integer_caution)
    FormKey.DMS -> stringResource(R.string.form_dms)
    FormKey.GEO_URI -> stringResource(R.string.form_geo)
}

@Composable
private fun GiveAddress(note: String, link: String, onNote: (String) -> Unit, share: () -> Unit) {
    Section(stringResource(R.string.address_title)) {
        OutlinedTextField(
            value = note,
            onValueChange = onNote,
            label = { Text(stringResource(R.string.address_note)) },
            supportingText = { Text(stringResource(R.string.address_count, note.length, NOTE_LIMIT)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            link,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = CodeStyle.fontFamily),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.card))
                .padding(Space.step2),
        )
        Button(onClick = share, shape = ButtonShape) { Text(stringResource(R.string.share)) }
    }
}

/**
 * The line to read out, in the words of the listener's language. The code is the
 * same ten characters in every language; only the words that carry them change,
 * and the listener writes it down from words they already know. The choice is
 * kept, because a courier reads to the same city all day.
 */
@Composable
private fun Aloud(
    spoken: String,
    listener: Spelling,
    voiced: Boolean?,
    onChoose: (Spelling) -> Unit,
    speak: () -> Unit,
) {
    val context = LocalContext.current
    Section(stringResource(R.string.aloud_title)) {
        Text(spoken, style = MaterialTheme.typography.bodyLarge)
        Listener(listener, onChoose)
        if (voiced == false) {
            Quiet(stringResource(R.string.aloud_no_voice, listener.name))
            TextButton(onClick = { addVoice(context) }) { Text(stringResource(R.string.aloud_add_voice)) }
        }
        Button(onClick = speak, enabled = voiced != false, shape = ButtonShape) { Text(stringResource(R.string.read_aloud)) }
    }
}

/** The listener's language, each offered under its own name with its first few words. */
@Composable
private fun Listener(chosen: Spelling, onChoose: (Spelling) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.aloud_listener)
    Column(verticalArrangement = Arrangement.spacedBy(Space.step1)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Quiet(stringResource(R.string.aloud_explain))
        Box {
            OutlinedButton(
                onClick = { open = true },
                shape = ButtonShape,
                modifier = Modifier.semantics { contentDescription = label + ", " + chosen.name },
            ) {
                Text(chosen.name + " \u00b7 " + chosen.sample)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                SPELLINGS.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(option.name)
                                Text(
                                    option.sample,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        leadingIcon = { RadioButton(selected = option == chosen, onClick = null) },
                        onClick = {
                            onChoose(option)
                            open = false
                        },
                    )
                }
            }
        }
    }
}

/** The speech engine's own page for adding voices, where it has one. */
private fun addVoice(context: Context) {
    val install = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(install) }
}

private fun metres(value: Double): String = String.format(Locale.getDefault(), "%.2f", value)

private fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null))
}

private fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.app_name), text))
}

private fun hasLocation(context: Context): Boolean =
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

/** Once refused twice, Android stops asking, and only the app's settings page can change the answer. */
private fun openSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    )
}
