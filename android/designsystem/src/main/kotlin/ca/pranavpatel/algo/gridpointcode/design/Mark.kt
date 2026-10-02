package ca.pranavpatel.algo.gridpointcode.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
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
    val density = LocalDensity.current
    val semantic = modifier.clearAndSetSemantics { contentDescription = spoken }

    // Left to right in every language, as the code is printed: laid out in a
    // right-to-left screen's order, the boxes read backwards, last character first.
    BoxWithConstraints(semantic) {
        // As large as asked, and no larger than the width allows. On a phone 360
        // wide the line did not fit its card: the last box was squeezed to
        // nothing and its character drawn bare against the edge. The hash is
        // set in type, which grows with the reader's text size; the boxes and
        // the gaps do not.
        val hashPerCell = with(density) { (HASH_EM * TYPE_PER_CELL).sp.toDp().value }
        val across = if (rows == 2) 5f * cell.value + 4 * GAP.value else (10f + 1f / 3f + hashPerCell) * cell.value + BESIDE.value
        val fitted = if (maxWidth.value >= across || maxWidth == Dp.Infinity) {
            cell
        } else if (rows == 2) {
            ((maxWidth.value - 4 * GAP.value) / 5f).dp
        } else {
            ((maxWidth.value - BESIDE.value) / (10f + 1f / 3f + hashPerCell)).dp
        }
        val height = fitted * 4 / 3
        val type = (fitted.value * TYPE_PER_CELL).sp
        val boxes: @Composable (Int) -> Unit = { index ->
            Cell(characters.getOrNull(index), colours.levels[index], colours.levelInks[index], fitted, height, type)
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            if (rows == 2) {
                Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(GAP)) { repeat(5) { boxes(it) } }
                    Row(horizontalArrangement = Arrangement.spacedBy(GAP)) { repeat(5) { boxes(it + 5) } }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GAP)) {
                    Text("#", style = CodeStyle.copy(fontSize = type), color = colours.inkSoft)
                    repeat(5) { boxes(it) }
                    Box(
                        Modifier
                            .padding(horizontal = 2.dp)
                            .size(width = fitted / 3, height = 2.dp)
                            .background(colours.inkSoft),
                    )
                    repeat(5) { boxes(it + 5) }
                }
            }
        }
    }
}

/** The gap between neighbours on the line. */
private val GAP = 3.dp

/** What the line takes besides its boxes and the hash: eleven gaps, and the hyphen's margins. */
private val BESIDE = GAP * 11 + 4.dp

/** A box's character, in sp, for each dp of the box's width. */
private const val TYPE_PER_CELL = 0.66f

/** The hash's width, in ems of its type, with room to spare: the mono face's advance is 0.6. */
private const val HASH_EM = 0.65f

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
