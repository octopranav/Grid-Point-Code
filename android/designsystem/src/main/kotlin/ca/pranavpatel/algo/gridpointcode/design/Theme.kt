package ca.pranavpatel.algo.gridpointcode.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em

/**
 * The colours Material has no role for.
 *
 * A code is always set in brass, a valid verdict is verdigris, and the ten level
 * tints are the format drawn as itself. None of these is a Material role, and
 * pressing one into a role that means something else would make every component
 * that reads that role quietly wrong.
 */
@Immutable
data class GpcColors(
    val code: Color,
    val valid: Color,
    val inkSoft: Color,
    val rule: Color,
    val levels: List<Color>,
    val levelInks: List<Color>,
)

private val LightGpc = GpcColors(
    code = brassLight,
    valid = verdigrisLight,
    inkSoft = inkSoftLight,
    rule = ruleLight,
    levels = listOf(
        level1Light, level2Light, level3Light, level4Light, level5Light,
        level6Light, level7Light, level8Light, level9Light, level10Light,
    ),
    levelInks = listOf(
        level1InkLight, level2InkLight, level3InkLight, level4InkLight, level5InkLight,
        level6InkLight, level7InkLight, level8InkLight, level9InkLight, level10InkLight,
    ),
)

private val DarkGpc = GpcColors(
    code = brassDark,
    valid = verdigrisDark,
    inkSoft = inkSoftDark,
    rule = ruleDark,
    levels = listOf(
        level1Dark, level2Dark, level3Dark, level4Dark, level5Dark,
        level6Dark, level7Dark, level8Dark, level9Dark, level10Dark,
    ),
    levelInks = listOf(
        level1InkDark, level2InkDark, level3InkDark, level4InkDark, level5InkDark,
        level6InkDark, level7InkDark, level8InkDark, level9InkDark, level10InkDark,
    ),
)

val LocalGpcColors = staticCompositionLocalOf { LightGpc }

/**
 * The three families, by role, bundled with the app as the site serves its own.
 *
 * Bitter, IBM Plex Sans and IBM Plex Mono are under the Open Font Licence, and
 * the files are the designers' own, unmodified, because both licences reserve
 * the name: `audit/fonts.py` holds each to the file it came from. Bitter and
 * Plex Sans come as variable fonts, one file for every weight, so each weight
 * the scale uses is asked of the weight axis by name; a variable font asked for
 * nothing is its default master, which for Bitter is its thinnest. Plex Mono
 * comes as three plain files. A character a face does not have, in a place name
 * written in another script, is drawn in the system's own face for it.
 */
private val Display = FontFamily(axis(R.font.bitter, 600))

private val Body = FontFamily(
    axis(R.font.ibm_plex_sans, 400),
    axis(R.font.ibm_plex_sans, 500),
    axis(R.font.ibm_plex_sans, 600),
)

private val Mono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
)

/** One weight of a variable font, set on its weight axis. */
private fun axis(resource: Int, weight: Int): Font =
    Font(resource, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

private fun family(of: Family): FontFamily = when (of) {
    Family.DISPLAY -> Display
    Family.BODY -> Body
    Family.MONO -> Mono
}

private fun Face.style(): TextStyle = TextStyle(
    fontFamily = family(family),
    fontSize = size,
    lineHeight = lineHeight,
    fontWeight = FontWeight(weight),
)

/** The style a code is set in: monospaced, tabular, and tracked so ten characters never touch. */
val CodeStyle: TextStyle = TypeScale.code.style().copy(
    letterSpacing = CODE_TRACKING_EM.em,
    fontFeatureSettings = "tnum",
)

private val GpcTypography = Typography(
    displayLarge = TypeScale.displayXl.style(),
    displayMedium = TypeScale.displayL.style(),
    displaySmall = TypeScale.displayM.style(),
    headlineLarge = TypeScale.displayL.style(),
    headlineMedium = TypeScale.displayM.style(),
    headlineSmall = TypeScale.displayM.style(),
    titleLarge = TypeScale.title.style(),
    titleMedium = TypeScale.title.style(),
    titleSmall = TypeScale.body.style().copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = TypeScale.bodyL.style(),
    bodyMedium = TypeScale.body.style(),
    bodySmall = TypeScale.small.style(),
    labelLarge = TypeScale.body.style().copy(fontWeight = FontWeight.Medium),
    labelMedium = TypeScale.small.style().copy(fontWeight = FontWeight.Medium),
    labelSmall = TypeScale.label.style(),
)

/**
 * The shape of every button.
 *
 * Material's buttons do not read the theme's shapes; they default to a full
 * pill. Here a pill only ever carries state, so an action takes the card's
 * square corners and has to be given them.
 */
val ButtonShape = RoundedCornerShape(Radius.card)

/** A survey instrument has square corners. A pill only ever carries state. */
private val GpcShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.cell),
    small = RoundedCornerShape(Radius.card),
    medium = RoundedCornerShape(Radius.card),
    large = RoundedCornerShape(Radius.card),
    extraLarge = RoundedCornerShape(Radius.card),
)

/**
 * The website's design system, drawn with Material's components.
 *
 * There is deliberately no colour taken from the wallpaper. The level tints and
 * the code, valid and invalid colours carry meaning, and every one of them was
 * checked against its own ink; a scheme computed from somebody's photograph was
 * checked against nothing.
 */
@Composable
fun GpcTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalGpcColors provides if (dark) DarkGpc else LightGpc) {
        MaterialTheme(
            colorScheme = if (dark) GpcDarkColors else GpcLightColors,
            typography = GpcTypography,
            shapes = GpcShapes,
            content = content,
        )
    }
}
