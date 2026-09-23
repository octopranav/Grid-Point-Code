package ca.pranavpatel.algo.gridpointcode.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The ten-cell mark: one box per character, one character per level, tinted by
 * depth so the eye starts where the world does.
 *
 * One character to a box is also the last answer to the confusable pairs the
 * typeface could not fully separate, C against G above all: nothing sits next to
 * anything.
 *
 * A screen reader hears [spoken] rather than ten boxes. Pass the read-aloud line
 * with its callout words; ten letters read one at a time are exactly the rhyming
 * sounds the callouts exist to avoid.
 */
@Composable
fun CodeMark(
    code: String,
    spoken: String,
    modifier: Modifier = Modifier,
    cell: Dp = 27.dp,
    rows: Int = 1,
) {
    val characters = code.filter { it.isLetterOrDigit() }.uppercase().take(10)
    val colours = LocalGpcColors.current
    val height = cell * 4 / 3
    val type = (cell.value * 0.66f).sp

    val boxes: @Composable (Int) -> Unit = { index ->
        Cell(characters.getOrNull(index), colours.levels[index], colours.levelInks[index], cell, height, type)
    }

    val semantic = modifier.clearAndSetSemantics { contentDescription = spoken }

    if (rows == 2) {
        Column(semantic, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { repeat(5) { boxes(it) } }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { repeat(5) { boxes(it + 5) } }
        }
        return
    }

    Row(semantic, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("#", style = CodeStyle.copy(fontSize = type), color = colours.inkSoft)
        repeat(5) { boxes(it) }
        Box(
            Modifier
                .padding(horizontal = 2.dp)
                .size(width = cell / 3, height = 2.dp)
                .background(colours.inkSoft),
        )
        repeat(5) { boxes(it + 5) }
    }
}

/**
 * One box. An empty one is drawn as a tint with no character rather than left
 * out: the length is fixed, and the mark should say so before it is complete.
 */
@Composable
private fun Cell(
    character: Char?,
    tint: androidx.compose.ui.graphics.Color,
    ink: androidx.compose.ui.graphics.Color,
    width: Dp,
    height: Dp,
    type: TextUnit,
) {
    Box(
        Modifier
            .size(width = width, height = height)
            .background(tint, RoundedCornerShape(Radius.cell)),
        contentAlignment = Alignment.Center,
    ) {
        if (character != null) {
            Text(character.toString(), style = CodeStyle.copy(fontSize = type, letterSpacing = 0.sp), color = ink)
        }
    }
}
