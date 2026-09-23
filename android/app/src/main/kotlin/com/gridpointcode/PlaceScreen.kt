package com.gridpointcode

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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

/**
 * The place, and everything that can be said about it.
 *
 * The same six groups the website's playground has, in the same order, minus
 * the ones that need a network until the map and the landmark archive arrive.
 */
@Composable
fun PlaceScreen(model: PlaceViewModel, speak: (String) -> Unit) {
    val ui by model.ui.collectAsState()
    val context = LocalContext.current

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { insets ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.step3, vertical = Space.step2),
            verticalArrangement = Arrangement.spacedBy(Space.step3),
        ) {
            Search(onOpen = { model.open(it) }, problem = ui.problem)
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
}

@Composable
private fun Search(onOpen: (String) -> Unit, problem: Problem?) {
    var text by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(Space.step0)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.search_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onOpen(text) }),
            trailingIcon = { TextButton(onClick = { onOpen(text) }) { Text(stringResource(R.string.search_go)) } },
            modifier = Modifier.fillMaxWidth(),
        )
        if (problem != null) {
            Text(
                text = describe(problem),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
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
