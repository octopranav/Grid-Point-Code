package ca.pranavpatel.algo.gridpointcode.design

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
 */
val CodeStyle: TextStyle = TextStyle(
    fontFamily = Mono,
    fontSize = TypeScale.code.size,
    lineHeight = TypeScale.code.lineHeight,
    fontWeight = FontWeight(TypeScale.code.weight),
    letterSpacing = CODE_TRACKING_EM.em,
    fontFeatureSettings = "tnum",
)
