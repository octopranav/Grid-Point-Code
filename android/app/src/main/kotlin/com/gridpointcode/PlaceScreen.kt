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
import androidx.activity.compose.BackHandler
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.withFrameNanos
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import com.gridpointcode.core.SavedOrder
import com.gridpointcode.core.savedRows
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.rotate
import com.gridpointcode.core.Doubt
import com.gridpointcode.core.Slip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.gridpointcode.core.Point
import com.gridpointcode.core.metresBetween
import com.gridpointcode.map.MapControl
import com.gridpointcode.map.rememberMapControl
import android.content.res.Configuration
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.gridpointcode.core.PRIVACY_ADDRESS
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
import androidx.compose.material3.Checkbox
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
import androidx.compose.foundation.layout.ColumnScope
import com.gridpointcode.core.Round
import com.gridpointcode.core.roundOf
import com.gridpointcode.core.addressOf
import java.util.Locale

/** Wider than this, the panel sits beside the map instead of over it. */
private const val WIDE_DP = 840

/** The panel's width beside the map, the same as the website's side panel. */
private const val PANE_DP = 420

/** How much of the panel shows over the map before it is pulled up: the code and its actions. */
private const val PEEK_DP = 300

/** The search bar's height over the map, with its margin. */
private const val SEARCH_DP = 96

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
    // The map, the saved places, or the settings: a tab bar on a phone, a rail
    // on a wide screen, as the canvas draws both.
    var tab by rememberSaveable { mutableStateOf(Tab.MAP) }
    val savedPoints = remember(saved) {
        saved.mapNotNull { place -> runCatching { selectionOf(place.code, Source.SAVED).point }.getOrNull() }
    }
    val wide = LocalConfiguration.current.screenWidthDp >= WIDE_DP
    // The keyboard: what has it, where Ctrl+K sends it, and where it goes back to.
    val typing = remember { Typing() }
    val screen = remember { FocusRequester() }
    val search = remember { FocusRequester() }
    val control = rememberMapControl()
    // A point asked about on the map, by a long press or a right-click.
    var pointed by remember { mutableStateOf<Pointed?>(null) }
    // Kept here rather than in the panel, because the map's menu can ask to save too.
    var editing by remember { mutableStateOf(false) }
    val sheet = rememberBottomSheetScaffoldState()
    val scope = rememberCoroutineScope()
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
                // The buttons' column: its margin, the widest button, a gap.
                creditEnd = Space.step3 + 56.dp + Space.step2,
                control = control,
                onMenu = { point, at -> pointed = Pointed(point, at) },
                modifier = Modifier.fillMaxSize(),
            )
            pointed?.let { asked ->
                PointMenu(
                    pointed = asked,
                    from = ui.selection.point,
                    onDismiss = { pointed = null },
                    onSelect = { model.place(selectionAt(asked.point, Source.MAP)) },
                    onSave = {
                        model.place(selectionAt(asked.point, Source.MAP))
                        editing = true
                    },
                    copy = { copy(context, it) },
                )
            }
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
                focus = search,
                onLeave = { screen.requestFocus() },
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
                // A mouse has no pinch; a wide window is where a mouse is likeliest.
                if (wide) Zoom(control)
                EmergencyButton(onClick = model::openEmergency)
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

    val savedPage: @Composable () -> Unit = {
        SavedPage(
            saved = saved,
            // Measured from where the reader is, or the place they are looking
            // at; never the opening example, which is nobody's.
            from = ui.selection.takeIf { it.source != Source.SAMPLE && it.source != Source.AREA }?.point,
            fromDevice = ui.selection.source == Source.DEVICE,
            onOpen = model::recall,
        )
    }
    val folded by model.folded.collectAsState()
    val packs by model.placePacks.collectAsState()
    val settingsPage: @Composable () -> Unit = {
        SettingsPage(
            basemap = basemap,
            onBasemap = model::choose,
            listener = ui.listener,
            onListener = model::readTo,
            folded = folded.size,
            onUnfold = model::unfoldAll,
            packs = packs,
            onListPacks = model::listPacks,
            onKeepPack = model::keepPack,
            onForgetPack = model::forgetPack,
        )
    }

    // Any place arriving, a link, a saved place opened, a code, is shown on the
    // map, whichever tab it arrived on; and Back from the other tabs is the map.
    LaunchedEffect(ui.selection) { tab = Tab.MAP }
    BackHandler(enabled = tab != Tab.MAP) { tab = Tab.MAP }

    // Focusable, and focused from the start, so a key pressed before anything
    // else is touched still reaches the shortcuts.
    LaunchedEffect(Unit) { screen.requestFocus() }
    CompositionLocalProvider(LocalTyping provides typing) {
    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(screen)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val asked = shortcutFor(event.key.nativeKeyCode, event.isCtrlPressed, event.isAltPressed, event.isShiftPressed, event.isMetaPressed, typing.active)
                if (tab != Tab.MAP) {
                    if (asked != Shortcut.SEARCH) return@onPreviewKeyEvent false
                    tab = Tab.MAP
                    scope.launch {
                        withFrameNanos { }
                        if (!wide) sheet.bottomSheetState.partialExpand()
                        search.requestFocus()
                    }
                    return@onPreviewKeyEvent true
                }
                when (asked) {
                    // On a phone the sheet may be covering the search box; it is
                    // lowered first, so the box the keys go to can be seen.
                    Shortcut.SEARCH -> scope.launch {
                        if (!wide) sheet.bottomSheetState.partialExpand()
                        search.requestFocus()
                    }
                    Shortcut.COPY -> copy(context, ui.formatted)
                    Shortcut.NUDGE_NORTH -> model.nudge(Compass.N)
                    Shortcut.NUDGE_EAST -> model.nudge(Compass.E)
                    Shortcut.NUDGE_SOUTH -> model.nudge(Compass.S)
                    Shortcut.NUDGE_WEST -> model.nudge(Compass.W)
                    null -> return@onPreviewKeyEvent false
                }
                true
            },
    ) {
    if (wide) {
        Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            RailTabs(tab, onTab = { tab = it })
            when (tab) {
                Tab.MAP -> {
                    map(Modifier.weight(1f).fillMaxHeight())
                    Panel(
                        ui = ui,
                        model = model,
                        speak = speak,
                        voiced = voiced,
                        editing = editing,
                        onEditing = { editing = it },
                        modifier = Modifier
                            .width(PANE_DP.dp)
                            .fillMaxHeight()
                            .statusBarsPadding()
                            .navigationBarsPadding(),
                    )
                }
                Tab.SAVED -> Box(Modifier.weight(1f).fillMaxHeight()) { savedPage() }
                Tab.SETTINGS -> Box(Modifier.weight(1f).fillMaxHeight()) { settingsPage() }
            }
        }
    } else {
        Scaffold(
            bottomBar = { PhoneTabs(tab, onTab = { tab = it }) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = MaterialTheme.colorScheme.background,
        ) { inner ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = inner.calculateBottomPadding()),
            ) {
                when (tab) {
                    Tab.MAP -> BottomSheetScaffold(
                        scaffoldState = sheet,
                        sheetContent = {
                            Panel(
                                ui = ui,
                                model = model,
                                speak = speak,
                                voiced = voiced,
                                editing = editing,
                                onEditing = { editing = it },
                            )
                        },
                        sheetPeekHeight = PEEK_DP.dp,
                        sheetContainerColor = MaterialTheme.colorScheme.background,
                        sheetShape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                    ) {
                        map(Modifier.fillMaxSize())
                    }
                    Tab.SAVED -> savedPage()
                    Tab.SETTINGS -> settingsPage()
                }
            }
        }
    }
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
    editing: Boolean,
    onEditing: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val anchoring by model.anchors.collectAsState()
    val keeping by model.offline.collectAsState()
    val saved by model.saved.collectAsState()
    val here = saved.savedAt(ui.selection.code)
    // The QR code for a link, shown in its own dialog until it is closed.
    var showing by remember { mutableStateOf<Qr?>(null) }
    showing?.let { QrDialog(it.title, it.code, it.link, onClose = { showing = null }) }
    val folded by model.folded.collectAsState()
    CompositionLocalProvider(LocalFolding provides Folding(folded, model::fold)) {
    if (editing) {
        SaveDialog(
            existing = here,
            note = ui.note,
            onSave = { label, note ->
                model.save(label, note)
                onEditing(false)
            },
            onRemove = {
                model.unsave(ui.selection.code)
                onEditing(false)
            },
            onDismiss = { onEditing(false) },
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
                showQr = { showing = Qr(context.getString(R.string.qr_area), area.cell, area.link) },
            )
            if (fromPlace) ShareArea(ui.areas, chosen = area.level, onChoose = model::widen)
            Spacer(Modifier.height(Space.step5))
            return@Column
        }
        ui.doubt?.let { Doubted(it, onUse = model::correct, onKeep = model::keep) }
        Head(
            ui = ui,
            saved = here,
            onBookmark = { onEditing(true) },
            speak = { speak(ui.spoken) },
            share = { share(context, ui.formatted + "\n" + ui.link) },
            copy = { copy(context, ui.formatted) },
            showQr = { showing = Qr(context.getString(R.string.qr_place), ui.formatted, ui.link) },
        )
        Nudge(ui.selection.code, ui.pad, onNudge = model::nudge)
        WrittenForms(ui.forms, copy = { copy(context, it) })
        GiveAddress(
            ui.note,
            ui.link,
            onNote = model::describeTheWay,
            share = { share(context, ui.formatted + "\n" + ui.link) },
            showQr = { showing = Qr(context.getString(R.string.qr_place), ui.formatted, ui.link) },
        )
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
}

/**
 * A typed code that lands far from the reader, offered the codes one slip away
 * that land near them. It never says the code is wrong: the specification
 * forbids claiming to detect a typo, and a code can be right and far. It says
 * where the code lands, and what one slip would have made of it.
 */
@Composable
private fun Doubted(doubt: Doubt, onUse: (String) -> Unit, onKeep: () -> Unit) {
    val colours = LocalGpcColors.current
    val reference = stringResource(if (doubt.fromDevice) R.string.doubt_you else R.string.doubt_place)
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(Radius.card),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colours.rule, RoundedCornerShape(Radius.card)),
    ) {
        Column(Modifier.padding(Space.step3), verticalArrangement = Arrangement.spacedBy(Space.step2)) {
            Text(
                stringResource(R.string.doubt_label).uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall,
                color = colours.inkSoft,
            )
            Text(
                stringResource(
                    R.string.doubt_far,
                    formatted(doubt.typed),
                    distance(doubt.metres),
                    direction(doubt.bearing),
                    reference,
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            Quiet(stringResource(R.string.doubt_heard))
            doubt.suggestions.forEach { suggestion ->
                Surface(
                    onClick = { onUse(suggestion.code) },
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(Radius.card),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(Radius.card)),
                ) {
                    Column(Modifier.padding(Space.step2), verticalArrangement = Arrangement.spacedBy(Space.step0)) {
                        Text(marked(suggestion.code, suggestion.slip), style = CodeStyle, color = colours.code)
                        val where = if (suggestion.metres < HERE_METRES) {
                            stringResource(if (doubt.fromDevice) R.string.doubt_at_you else R.string.doubt_at_place)
                        } else {
                            stringResource(R.string.doubt_away, distance(suggestion.metres), direction(suggestion.bearing), reference)
                        }
                        val how = when (val slip = suggestion.slip) {
                            is Slip.Replaced -> stringResource(R.string.doubt_replaced, slip.position, slip.was.toString())
                            is Slip.Swapped -> stringResource(R.string.doubt_swapped, slip.position, slip.position + 1)
                        }
                        Text(
                            "$where \u00b7 $how",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Quiet(stringResource(R.string.doubt_checked, doubt.checked, reference))
            TextButton(onClick = onKeep) { Text(stringResource(R.string.doubt_keep, formatted(doubt.typed))) }
        }
    }
}

/** Closer than this, a suggestion is where the reader is, not some metres off it. */
private const val HERE_METRES = 5.0

/** The code written out, with the characters one slip changed underlined. */
private fun marked(code: String, slip: Slip): AnnotatedString {
    val changed = when (slip) {
        is Slip.Replaced -> setOf(slip.position)
        is Slip.Swapped -> setOf(slip.position, slip.position + 1)
    }
    // Position p of the bare code is character p of the written form, after the
    // hash, and one further on past the hyphen.
    val at = changed.map { if (it <= 5) it else it + 1 }.toSet()
    val written = formatted(code)
    return buildAnnotatedString {
        written.forEachIndexed { index, character ->
            if (index in at) withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(character) }
            else append(character)
        }
    }
}

/** Eight points of the compass, as words. */
@Composable
private fun direction(octant: String): String = stringResource(
    when (octant) {
        "N" -> R.string.dir_n
        "NE" -> R.string.dir_ne
        "E" -> R.string.dir_e
        "SE" -> R.string.dir_se
        "S" -> R.string.dir_s
        "SW" -> R.string.dir_sw
        "W" -> R.string.dir_w
        else -> R.string.dir_nw
    },
)

private const val SEARCH_FIELD = "search"
private const val NOTE_FIELD = "note"

/** A point asked about on the map, and where on the map it was asked. */
private data class Pointed(val point: Point, val at: Offset)

/**
 * What can be done with a point asked about on the map: its code, how far it
 * is from the place already chosen, and choosing it, saving it, or copying it
 * without leaving the place that is chosen now.
 */
@Composable
private fun PointMenu(
    pointed: Pointed,
    from: Point,
    onDismiss: () -> Unit,
    onSelect: () -> Unit,
    onSave: () -> Unit,
    copy: (String) -> Unit,
) {
    val code = remember(pointed) { formatted(selectionAt(pointed.point, Source.MAP).code) }
    val decimal = remember(pointed) {
        String.format(Locale.ROOT, "%.6f, %.6f", pointed.point.latitude, pointed.point.longitude)
    }
    val away = remember(pointed, from) { metresBetween(from, pointed.point) }
    Box(Modifier.offset { IntOffset(pointed.at.x.roundToInt(), pointed.at.y.roundToInt()) }) {
        DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
            Column(Modifier.padding(horizontal = Space.step3, vertical = Space.step1)) {
                Text(code, style = CodeStyle, color = LocalGpcColors.current.code)
                Text(
                    stringResource(R.string.menu_away, distance(away)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            val item: @Composable (Int, () -> Unit) -> Unit = { label, act ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        act()
                        onDismiss()
                    },
                )
            }
            item(R.string.menu_select, onSelect)
            item(R.string.menu_save, onSave)
            item(R.string.menu_copy_code) { copy(code) }
            item(R.string.menu_copy_point) { copy(decimal) }
        }
    }
}

/** A step in and a step out, for a mouse. */
@Composable
private fun Zoom(control: MapControl) {
    listOf(
        Triple(ZoomIn, R.string.zoom_in, control::zoomIn),
        Triple(ZoomOut, R.string.zoom_out, control::zoomOut),
    ).forEach { (icon, name, step) ->
        val label = stringResource(name)
        SmallFloatingActionButton(
            onClick = step,
            shape = ButtonShape,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { contentDescription = label },
        ) {
            Icon(icon, contentDescription = null)
        }
    }
}

/** A page of the site, in the reader's browser. */
private fun open(context: Context, address: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(address))) }
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
    focus: FocusRequester,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val typing = LocalTyping.current
    DisposableEffect(Unit) { onDispose { typing.mark(SEARCH_FIELD, false) } }
    // The map is what answers, so the keyboard gets out of its way, and the
    // arrow keys go back to nudging the place.
    val go = {
        keyboard?.hide()
        onGo(text)
        onLeave()
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
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .typing(typing, SEARCH_FIELD)
                    .onPreviewKeyEvent { event ->
                        // Escape leaves the field, as it leaves any search box.
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                            keyboard?.hide()
                            onLeave()
                            true
                        } else {
                            false
                        }
                    },
            )
            Found(
                finding = finding,
                onPick = { place ->
                    keyboard?.hide()
                    onLeave()
                    onPick(place)
                },
                onRecall = { place ->
                    keyboard?.hide()
                    onLeave()
                    onRecall(place)
                },
                onInstead = { at ->
                    keyboard?.hide()
                    onLeave()
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
    Section(stringResource(R.string.area_title), key = "areas") {
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
    showQr: () -> Unit,
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
            Actions(speak = speak, share = share, copy = copy, showQr = showQr)
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

/** A saved place's name, or its code when it has none, with the code and the directions under it. */
/** Closer than any two cells' centres can be: the same place. */
private const val SAME_PLACE_METRES = 1.0

/** An arrow in a disc, turned to point at a saved place. */
@Composable
private fun Toward(heading: Double) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Arrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(20.dp)
                .rotate(heading.toFloat()),
        )
    }
}

/** How far a saved place is, and which way, the way read aloud as a word. */
@Composable
private fun Away(metres: Double, octant: String) {
    val word = direction(octant)
    Column(horizontalAlignment = Alignment.End) {
        Text(distance(metres), style = CodeStyle)
        Text(
            octant,
            style = MaterialTheme.typography.labelSmall,
            color = LocalGpcColors.current.inkSoft,
            modifier = Modifier.semantics { contentDescription = word },
        )
    }
}

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
/** What the saved page shows: the list, stops being chosen for a round, or the round. */
private enum class SavedMode { LIST, CHOOSING, ROUND }

@Composable
private fun SavedPage(
    saved: List<SavedPlace>,
    from: Point?,
    fromDevice: Boolean,
    onOpen: (SavedPlace) -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(SavedMode.LIST) }
    var chosen by rememberSaveable { mutableStateOf(listOf<String>()) }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = Space.step3, vertical = Space.step3),
            verticalArrangement = Arrangement.spacedBy(Space.step2),
        ) {
            when (mode) {
                SavedMode.LIST -> SavedList(saved, from, fromDevice, onOpen, onRound = {
                    chosen = emptyList()
                    mode = SavedMode.CHOOSING
                })
                SavedMode.CHOOSING -> ChoosingStops(
                    saved = saved,
                    chosen = chosen,
                    onChosen = { chosen = it },
                    onOrder = { mode = SavedMode.ROUND },
                    onCancel = { mode = SavedMode.LIST },
                )
                SavedMode.ROUND -> WalkingOrder(
                    round = remember(saved, chosen, from) { roundOf(saved.filter { it.code in chosen }, from) },
                    fromDevice = fromDevice,
                    started = from != null,
                    onOpen = onOpen,
                    onDone = { mode = SavedMode.LIST },
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.SavedList(
    saved: List<SavedPlace>,
    from: Point?,
    fromDevice: Boolean,
    onOpen: (SavedPlace) -> Unit,
    onRound: () -> Unit,
) {
    var order by rememberSaveable { mutableStateOf(SavedOrder.NEAREST) }
    val rows = remember(saved, from, order) { savedRows(saved, from, order) }
    Text(stringResource(R.string.tab_saved), style = MaterialTheme.typography.displaySmall)
    Quiet(stringResource(R.string.saved_kept))
    if (saved.isEmpty()) Quiet(stringResource(R.string.saved_empty))
    // The order only means something when there is a point to measure from.
    if (saved.isNotEmpty() && from != null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.step1)) {
            FilterChip(
                selected = order == SavedOrder.NEAREST,
                onClick = { order = SavedOrder.NEAREST },
                label = { Text(stringResource(R.string.saved_nearest)) },
            )
            FilterChip(
                selected = order == SavedOrder.RECENT,
                onClick = { order = SavedOrder.RECENT },
                label = { Text(stringResource(R.string.saved_recent)) },
            )
        }
        Quiet(
            pluralStringResource(R.plurals.saved_count, saved.size, saved.size) + " \u00b7 " +
                stringResource(if (fromDevice) R.string.saved_from_you else R.string.saved_from_place),
        )
    }
    // A round needs two stops to put in an order.
    if (saved.size >= 2) {
        OutlinedButton(onClick = onRound, shape = ButtonShape) { Text(stringResource(R.string.round_start)) }
    }
    LazyColumn(Modifier.weight(1f)) {
        items(rows, key = { it.place.code }) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onOpen(row.place) }
                    .padding(vertical = Space.step2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.step2),
            ) {
                val metres = row.metres
                val octant = row.octant
                // In the same cell a direction means nothing: the place is here. A
                // cell away is the next door, and gets its distance and its way.
                val here = metres != null && metres < SAME_PLACE_METRES
                row.heading?.takeUnless { here }?.let { Toward(it) }
                Box(Modifier.weight(1f)) { SavedLines(row.place) }
                when {
                    here -> Text(stringResource(R.string.saved_here), style = CodeStyle)
                    metres != null && octant != null -> Away(metres, octant)
                }
            }
            HorizontalDivider(color = LocalGpcColors.current.rule)
        }
    }
}

/** The saved places with a box each, to choose the stops of a round. */
@Composable
private fun ColumnScope.ChoosingStops(
    saved: List<SavedPlace>,
    chosen: List<String>,
    onChosen: (List<String>) -> Unit,
    onOrder: () -> Unit,
    onCancel: () -> Unit,
) {
    Text(stringResource(R.string.round_title), style = MaterialTheme.typography.displaySmall)
    Quiet(stringResource(R.string.round_choose))
    LazyColumn(Modifier.weight(1f)) {
        items(saved, key = { it.code }) { place ->
            val picked = place.code in chosen
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = picked, role = Role.Checkbox) { onChosen(if (it) chosen + place.code else chosen - place.code) }
                    .padding(vertical = Space.step1),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.step2),
            ) {
                Checkbox(checked = picked, onCheckedChange = null)
                Box(Modifier.weight(1f)) { SavedLines(place) }
            }
            HorizontalDivider(color = LocalGpcColors.current.rule)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Space.step2), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = onOrder, enabled = chosen.size >= 2, shape = ButtonShape) {
            Text(pluralStringResource(R.plurals.round_order, chosen.size, chosen.size))
        }
        TextButton(onClick = onCancel) { Text(stringResource(R.string.round_cancel)) }
    }
}

/**
 * The chosen stops in the order to walk them, each with its leg from the one
 * before, the whole in straight lines, and the list to share in that order.
 */
@Composable
private fun ColumnScope.WalkingOrder(
    round: Round,
    fromDevice: Boolean,
    started: Boolean,
    onOpen: (SavedPlace) -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val total = distance(round.metres)
    val count = round.stops.size
    val heading = pluralStringResource(R.plurals.round_summary, count, count, total)
    Text(stringResource(R.string.round_title), style = MaterialTheme.typography.displaySmall)
    Quiet(heading)
    Quiet(
        stringResource(
            when {
                !started -> R.string.round_from_first
                fromDevice -> R.string.round_from_you
                else -> R.string.round_from_place
            },
        ),
    )
    LazyColumn(Modifier.weight(1f)) {
        items(round.stops, key = { it.place.code }) { stop ->
            val number = round.stops.indexOf(stop) + 1
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onOpen(stop.place) }
                    .padding(vertical = Space.step2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.step2),
            ) {
                StopNumber(number)
                Box(Modifier.weight(1f)) { SavedLines(stop.place) }
                // The first stop of a round with no start is where it starts.
                if (started || number > 1) Text(distance(stop.leg), style = CodeStyle)
            }
            HorizontalDivider(color = LocalGpcColors.current.rule)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Space.step2), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = { share(context, roundText(heading, round)) }, shape = ButtonShape) {
            Text(stringResource(R.string.round_share))
        }
        TextButton(onClick = onDone) { Text(stringResource(R.string.round_done)) }
    }
}

/** A stop's place in the round, in the theme's own colour. */
@Composable
private fun StopNumber(number: Int) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(number.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
    }
}

/** The round as text to send: its summary, then a numbered line a stop, each with its link. */
private fun roundText(heading: String, round: Round): String =
    (listOf(heading) + round.stops.mapIndexed { index, stop ->
        val name = stop.place.label.ifEmpty { formatted(stop.place.code) }
        "${index + 1}. $name ${addressOf(stop.place.code, stop.place.note)}"
    }).joinToString("\n")

/** The three tabs, as the canvas names them. */
private enum class Tab { MAP, SAVED, SETTINGS }

private val tabs = listOf(
    Triple(Tab.MAP, MapIcon, R.string.tab_map),
    Triple(Tab.SAVED, Bookmark, R.string.tab_saved),
    Triple(Tab.SETTINGS, SettingsIcon, R.string.tab_settings),
)

/** The tab bar a phone shows along the bottom. */
@Composable
private fun PhoneTabs(tab: Tab, onTab: (Tab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        tabs.forEach { (which, icon, label) ->
            NavigationBarItem(
                selected = tab == which,
                onClick = { onTab(which) },
                icon = { Icon(icon, contentDescription = null) },
                label = { Text(stringResource(label)) },
            )
        }
    }
}

/** The same tabs as a rail down the side of a wide screen. */
@Composable
private fun RailTabs(tab: Tab, onTab: (Tab) -> Unit) {
    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
        Spacer(Modifier.height(Space.step3))
        tabs.forEach { (which, icon, label) ->
            NavigationRailItem(
                selected = tab == which,
                onClick = { onTab(which) },
                icon = { Icon(icon, contentDescription = null) },
                label = { Text(stringResource(label)) },
            )
        }
    }
}

/**
 * What the reader sets once and leaves: the map they look at, whose words a
 * code is read out in, the panel's folded sections, and where the privacy page,
 * the notices and the version are.
 */
@Composable
private fun SettingsPage(
    basemap: Basemap,
    onBasemap: (Basemap) -> Unit,
    listener: Spelling,
    onListener: (Spelling) -> Unit,
    folded: Int,
    onUnfold: () -> Unit,
    packs: Packs,
    onListPacks: () -> Unit,
    onKeepPack: (PlacePacks.Offered) -> Unit,
    onForgetPack: (String) -> Unit,
) {
    val context = LocalContext.current
    var noticing by remember { mutableStateOf(false) }
    var choosingPack by remember { mutableStateOf(false) }
    if (choosingPack) PacksDialog(packs, onKeep = onKeepPack, onDismiss = { choosingPack = false })
    if (noticing) NoticesPage(onClose = { noticing = false })
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = Space.step3, vertical = Space.step3),
            verticalArrangement = Arrangement.spacedBy(Space.step3),
        ) {
            Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.displaySmall)

            Group(stringResource(R.string.basemap)) {
                Column(Modifier.selectableGroup()) {
                    Basemap.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = option == basemap, role = Role.RadioButton) { onBasemap(option) }
                                .padding(vertical = Space.step1),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.step2),
                        ) {
                            RadioButton(selected = option == basemap, onClick = null)
                            Text(stringResource(basemapName(option)))
                        }
                    }
                }
            }

            Group(stringResource(R.string.aloud_title)) { Listener(listener, onListener) }

            Group(stringResource(R.string.packs_title)) {
                Quiet(stringResource(R.string.packs_about))
                packs.kept.forEach { kept ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(kept.name, style = MaterialTheme.typography.bodyLarge)
                            Quiet(Formatter.formatShortFileSize(context, kept.bytes))
                        }
                        TextButton(onClick = { onForgetPack(kept.code) }) { Text(stringResource(R.string.packs_forget)) }
                    }
                }
                when (packs.note) {
                    Packs.Note.FAILED -> Quiet(stringResource(R.string.packs_failed, packs.failed.orEmpty()))
                    Packs.Note.UNREACHABLE -> Quiet(stringResource(R.string.packs_unreachable))
                    null -> Unit
                }
                OutlinedButton(
                    onClick = {
                        choosingPack = true
                        onListPacks()
                    },
                    shape = ButtonShape,
                ) { Text(stringResource(R.string.packs_keep)) }
            }

            if (folded > 0) {
                Group(stringResource(R.string.settings_panel)) {
                    Quiet(pluralStringResource(R.plurals.settings_folded, folded, folded))
                    OutlinedButton(onClick = onUnfold, shape = ButtonShape) { Text(stringResource(R.string.settings_unfold)) }
                }
            }

            // Named only where there is a keyboard to press them with.
            if (LocalConfiguration.current.keyboard == Configuration.KEYBOARD_QWERTY) {
                Group(stringResource(R.string.settings_keys)) { Quiet(stringResource(R.string.keys_hint)) }
            }

            Group(stringResource(R.string.settings_about)) {
                Text(stringResource(R.string.settings_version, version), style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = { open(context, PRIVACY_ADDRESS) }) { Text(stringResource(R.string.privacy)) }
                TextButton(onClick = { noticing = true }) { Text(stringResource(R.string.notices_title)) }
                TextButton(onClick = { open(context, SOURCE_ADDRESS) }) { Text(stringResource(R.string.settings_source)) }
            }
        }
    }
}

/**
 * Every country's place names, to keep one: the list the Landmarks workflow
 * publishes, filtered as a name is typed, each with what it costs to fetch.
 */
@Composable
private fun PacksDialog(packs: Packs, onKeep: (PlacePacks.Offered) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var filter by remember { mutableStateOf("") }
    val kept = packs.kept.map { it.code }.toSet()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.packs_keep)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.step2)) {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    label = { Text(stringResource(R.string.packs_filter)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val offered = packs.offered
                when {
                    packs.listing -> Quiet(stringResource(R.string.packs_listing))
                    offered == null -> Quiet(stringResource(R.string.packs_unreachable))
                    else -> LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        val shown = offered.filter { it.name.contains(filter.trim(), ignoreCase = true) }
                        items(shown, key = { it.code }) { pack ->
                            val busy = packs.keeping == pack.code
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = packs.keeping == null && pack.code !in kept, role = Role.Button) { onKeep(pack) }
                                    .padding(vertical = Space.step2),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(pack.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    when {
                                        busy -> stringResource(R.string.packs_keeping)
                                        pack.code in kept -> stringResource(R.string.packs_kept)
                                        else -> Formatter.formatShortFileSize(context, pack.download)
                                    },
                                    style = CodeStyle,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.packs_done)) } },
    )
}

/** A heading and what it governs, on the settings page. */
@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.step1)) {
        HorizontalDivider(color = LocalGpcColors.current.rule)
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        content()
    }
}

/** Where the app's source is, and the format's with it. */
private const val SOURCE_ADDRESS = "https://github.com/octopranav/Grid-Point-Code"

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
    showQr: () -> Unit,
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
            Actions(speak = speak, share = share, copy = copy, showQr = showQr)
        }
    }
}

/**
 * The four things done with what the card shows, as the canvas draws them: a
 * row of tiles, each an icon over its word, reading aloud first and filled,
 * because saying the code is what the card is most often for.
 */
@Composable
private fun Actions(speak: () -> Unit, share: () -> Unit, copy: () -> Unit, showQr: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.step1)) {
        Tile(Speak, R.string.read_aloud, speak, primary = true, modifier = Modifier.weight(1f))
        Tile(ShareIcon, R.string.share, share, modifier = Modifier.weight(1f))
        Tile(CopyIcon, R.string.copy, copy, modifier = Modifier.weight(1f))
        Tile(QrIcon, R.string.qr_button, showQr, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Tile(icon: ImageVector, label: Int, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) {
    Surface(
        onClick = onClick,
        shape = ButtonShape,
        color = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = modifier.heightIn(min = 60.dp),
    ) {
        Column(
            modifier = Modifier.padding(vertical = Space.step1, horizontal = Space.step0),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.step0, Alignment.CenterVertically),
        ) {
            Icon(icon, contentDescription = null, tint = LocalContentColor.current, modifier = Modifier.size(22.dp))
            Text(stringResource(label), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
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
    Section(stringResource(R.string.anchor_title), key = "anchor") {
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
private fun Section(title: String, key: String, content: @Composable () -> Unit) {
    val folding = LocalFolding.current
    val open = key !in folding.folded
    val state = stringResource(if (open) R.string.section_open else R.string.section_folded)
    val act = stringResource(if (open) R.string.section_fold else R.string.section_unfold)
    Column(verticalArrangement = Arrangement.spacedBy(Space.step2)) {
        HorizontalDivider(color = LocalGpcColors.current.rule)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = act, role = Role.Button) { folding.toggle(key) }
                .semantics {
                    heading()
                    stateDescription = state
                }
                .padding(vertical = Space.step0),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(
                Chevron,
                contentDescription = null,
                tint = LocalGpcColors.current.inkSoft,
                modifier = Modifier.rotate(if (open) 180f else 0f),
            )
        }
        if (open) content()
    }
}

/**
 * Which of the panel's sections are folded, and how to fold or open one. Every
 * section is open until the reader folds it; what they fold stays folded, since
 * a reader who never nudges should not scroll past the pad every time.
 */
private class Folding(val folded: Set<String>, val toggle: (String) -> Unit)

private val LocalFolding = compositionLocalOf { Folding(emptySet()) {} }

@Composable
private fun Nudge(code: String, pad: Map<Compass, String>, onNudge: (Compass) -> Unit) {
    Section(stringResource(R.string.nudge_title), key = "nudge") {
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
    Section(stringResource(R.string.forms_title), key = "forms") {
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
private fun GiveAddress(note: String, link: String, onNote: (String) -> Unit, share: () -> Unit, showQr: () -> Unit) {
    val typing = LocalTyping.current
    DisposableEffect(Unit) { onDispose { typing.mark(NOTE_FIELD, false) } }
    Section(stringResource(R.string.address_title), key = "address") {
        OutlinedTextField(
            value = note,
            onValueChange = onNote,
            label = { Text(stringResource(R.string.address_note)) },
            supportingText = { Text(stringResource(R.string.address_count, note.length, NOTE_LIMIT)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .typing(typing, NOTE_FIELD),
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
        Row(horizontalArrangement = Arrangement.spacedBy(Space.step1)) {
            Button(onClick = share, shape = ButtonShape) { Text(stringResource(R.string.share)) }
            OutlinedButton(onClick = showQr, shape = ButtonShape) { Text(stringResource(R.string.qr_button)) }
        }
    }
}

/** A QR code to show: what it opens, written out, and the link it carries. */
private data class Qr(val title: String, val code: String, val link: String)

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
    Section(stringResource(R.string.aloud_title), key = "aloud") {
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
