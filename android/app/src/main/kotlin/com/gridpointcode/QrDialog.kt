package com.gridpointcode

import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import ca.pranavpatel.algo.gridpointcode.design.CodeStyle
import ca.pranavpatel.algo.gridpointcode.design.LocalGpcColors
import ca.pranavpatel.algo.gridpointcode.design.Radius
import ca.pranavpatel.algo.gridpointcode.design.Space
import com.gridpointcode.core.QrSymbol
import com.gridpointcode.core.qrFor
import kotlin.math.floor

/**
 * A QR code for a link, to be scanned off this screen by a phone across the
 * counter, with or without this app on it.
 *
 * Dark on white in either theme, as the website draws it: the standard asks for
 * dark modules on a light ground, and this is read by a camera nobody here has
 * seen. The screen goes to full brightness while it is shown, because a symbol
 * on a dim screen in daylight does not scan, and goes back when it closes.
 */
@Composable
fun QrDialog(title: String, code: String, link: String, onClose: () -> Unit) {
    val symbol = remember(link) { qrFor(link) }
    Dialog(onDismissRequest = onClose) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.let { it.attributes = it.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL } }
            onDispose {
                window?.let { it.attributes = it.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(Radius.card)) {
            Column(Modifier.padding(Space.step3), verticalArrangement = Arrangement.spacedBy(Space.step2)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Plate(
                    symbol = symbol,
                    describe = stringResource(R.string.qr_describe, link),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                )
                Text(code, style = CodeStyle, color = LocalGpcColors.current.code)
                Text(
                    link,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = CodeStyle.fontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.qr_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onClose) { Text(stringResource(R.string.emergency_close)) }
            }
        }
    }
}

/**
 * The symbol on its own white plate, each module a whole number of pixels, so
 * no module is drawn blurred across two and a camera sees clean edges.
 */
@Composable
private fun Plate(symbol: QrSymbol, describe: String, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .background(Color.White)
            .semantics { contentDescription = describe },
    ) {
        val module = floor(size.minDimension / symbol.size)
        val start = Offset((size.width - module * symbol.size) / 2, (size.height - module * symbol.size) / 2)
        for (y in 0 until symbol.size) {
            for (x in 0 until symbol.size) {
                if (symbol.isDark(x, y)) {
                    drawRect(Color.Black, topLeft = start + Offset(x * module, y * module), size = Size(module, module))
                }
            }
        }
    }
}
