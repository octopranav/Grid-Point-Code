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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import com.gridpointcode.core.Named
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
fun PlaceScreen(model: PlaceViewModel, speak: (String) -> Unit) {
    val ui by model.ui.collectAsState()
    val basemap by model.basemap.collectAsState()
    val finding by model.found.collectAsState()
    val wide = LocalConfiguration.current.screenWidthDp >= WIDE_DP
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + SEARCH_DP.dp
    val bottom = if (wide) WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() else PEEK_DP.dp

    val context = LocalContext.current
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
                modifier = Modifier.fillMaxSize(),
            )
            Search(
                text = model.query,
                onType = model::type,
                onGo = model::go,
                finding = finding,
                onPick = model::pick,
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
                Basemaps(chosen = basemap, onChoose = model::choose)
                Locate(locating = ui.locating, onClick = locate)
            }
        }
    }

    if (wide) {
        Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            map(Modifier.weight(1f).fillMaxHeight())
            Panel(
                ui = ui,
                model = model,
                speak = speak,
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
                Panel(ui = ui, model = model, speak = speak, modifier = Modifier.navigationBarsPadding())
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
private fun Panel(ui: PlaceView, model: PlaceViewModel, speak: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.step3, vertical = Space.step2),
        verticalArrangement = Arrangement.spacedBy(Space.step3),
    ) {
        Head(
            ui = ui,
            speak = { speak(ui.spoken) },
            share = { share(context, ui.formatted + "\n" + ui.link) },
            copy = { copy(context, ui.formatted) },
        )
        Nudge(ui.selection.code, ui.pad, onNudge = model::nudge)
        WrittenForms(ui.forms, copy = { copy(context, it) })
        GiveAddress(ui.note, ui.link, onNote = model::describeTheWay, share = { share(context, ui.formatted + "\n" + ui.link) })
        Aloud(ui.spoken, speak = { speak(ui.spoken) })
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
private fun Found(finding: Finding, onPick: (Named) -> Unit) {
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
    if (finding.places.isEmpty()) return
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = FOUND_DP.dp)
            .verticalScroll(rememberScrollState()),
    ) {
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
    Problem.LocationRefused -> stringResource(R.string.problem_location_refused)
    Problem.LocationOff -> stringResource(R.string.problem_location_off)
    Problem.NoFix -> stringResource(R.string.problem_no_fix)
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
private fun Head(ui: PlaceView, speak: () -> Unit, share: () -> Unit, copy: () -> Unit) {
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
                text = sourceLabel(ui.selection.source).uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall,
                color = colours.inkSoft,
            )
            CodeMark(code = ui.selection.code, spoken = ui.spoken)
            Text(
                text = stringResource(R.string.cell_size, metres(ui.cell.northSouthMetres), metres(ui.cell.eastWestMetres)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    },
)

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

@Composable
private fun Aloud(spoken: String, speak: () -> Unit) {
    Section(stringResource(R.string.aloud_title)) {
        Text(spoken, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = speak, shape = ButtonShape) { Text(stringResource(R.string.read_aloud)) }
    }
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
