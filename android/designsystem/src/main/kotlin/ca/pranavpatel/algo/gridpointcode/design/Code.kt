package ca.pranavpatel.algo.gridpointcode.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.em

/**
 * IBM Plex Mono, the face a code is set in, as three plain files.
 *
 * Kept apart from the theme, in a file of its own, so that an app which sets
 * only codes ships only this face. The watch draws everything else in the
 * watch's own type, and its release is shrunk: with nothing of the theme's file
 * in use, the shrinker removes it, and with it Bitter and Plex Sans, which are
 * two thirds of the fonts. Were this in the theme's file, the file's own
 * initialiser would keep all three families alive for the sake of one.
 */
internal val Mono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
)

/**
 * The style a code is set in: monospaced, tabular, and tracked so ten characters
 * never touch. The type scale sets a code in the mono family; this reads the
 * family directly rather than through the theme's lookup, for the reason above.
 *
 * Always left to right, as a code is printed on a sign, in a language written
 * either way. Set in a right-to-left paragraph a short form's leading hyphen
 * moved to its end, the hash left the front of a code, and a latitude and
 * longitude could be drawn in the wrong order.
 */
val CodeStyle: TextStyle = TextStyle(
    fontFamily = Mono,
    fontSize = TypeScale.code.size,
    lineHeight = TypeScale.code.lineHeight,
    fontWeight = FontWeight(TypeScale.code.weight),
    letterSpacing = CODE_TRACKING_EM.em,
    fontFeatureSettings = "tnum",
    textDirection = TextDirection.Ltr,
)

/**
 * [text], a code, a short form or coordinates, kept left to right inside a
 * sentence or a line of other words on a right-to-left screen: isolated as a run
 * of its own, so the bidirectional algorithm cannot carry its hash or its leading
 * hyphen to the far end, as it did on the saved list. On a left-to-right screen
 * it is returned as it is, so nothing changes where nothing was wrong.
 */
@Composable
fun leftToRight(text: String): String =
    if (LocalLayoutDirection.current == LayoutDirection.Rtl) "\u2066$text\u2069" else text
