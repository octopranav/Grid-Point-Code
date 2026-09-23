// Generated from design/tokens.json by design/build-tokens.mjs. Do not edit by hand.

package ca.pranavpatel.algo.gridpointcode.design

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Which of the three typefaces a step of the scale is set in. */
enum class Family { DISPLAY, BODY, MONO }

/** One step of the type scale. */
class Face(val size: TextUnit, val lineHeight: TextUnit, val weight: Int, val family: Family)

object Space {
    val step0 = 4.dp
    val step1 = 6.dp
    val step2 = 11.dp
    val step3 = 18.dp
    val step4 = 28.dp
    val step5 = 44.dp
    val step6 = 64.dp
    val step7 = 96.dp
}

object Radius {
    val cell = 2.dp
    val card = 3.dp
    val pill = 999.dp
}

object TypeScale {
    val displayXl = Face(44.sp, 48.sp, 600, Family.DISPLAY)
    val displayL = Face(32.sp, 37.sp, 600, Family.DISPLAY)
    val displayM = Face(24.sp, 29.sp, 600, Family.DISPLAY)
    val title = Face(19.sp, 25.sp, 600, Family.BODY)
    val bodyL = Face(16.5.sp, 27.sp, 400, Family.BODY)
    val body = Face(15.5.sp, 25.sp, 400, Family.BODY)
    val small = Face(13.5.sp, 21.sp, 400, Family.BODY)
    val label = Face(11.sp, 13.sp, 400, Family.MONO)
    val codeXl = Face(30.sp, 36.sp, 600, Family.MONO)
    val code = Face(16.sp, 22.sp, 500, Family.MONO)
}

/** How far apart the characters of a code are set, in em. */
const val CODE_TRACKING_EM = 0.02f
