package com.gridpointcode.wear

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import ca.pranavpatel.algo.gridpointcode.design.CodeStyle
import ca.pranavpatel.algo.gridpointcode.design.inkDark
import ca.pranavpatel.algo.gridpointcode.design.inkMidDark
import ca.pranavpatel.algo.gridpointcode.design.inkSoftDark
import com.gridpointcode.notices.Block
import com.gridpointcode.notices.blocksOf
import com.gridpointcode.notices.componentsIn
import com.gridpointcode.notices.readNotice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Everything the watch's notices page shows, read from the assets and laid out
 * off the main thread: the Play services notices run to half a megabyte, and a
 * watch is slower than a phone.
 */
private class WatchNotices(
    val typefaces: List<Block>,
    val apache: List<String>,
    val apacheNotice: List<Block>,
    val protobuf: List<Block>,
    val play: List<String>,
    val playNotices: List<Block>,
)

private fun readWatchNotices(context: Context): WatchNotices? {
    val components = componentsIn(readNotice(context, "components.txt") ?: return null)
    fun blocks(name: String) = readNotice(context, name)?.let(::blocksOf)
    return WatchNotices(
        typefaces = (blocks("bitter-OFL.txt") ?: return null) + Block.Rule + (blocks("ibm-plex-OFL.txt") ?: return null),
        apache = components.filter { it.licence == "Apache-2.0" }.map { it.module },
        apacheNotice = blocks("apache-2.0.txt") ?: return null,
        protobuf = blocks("protobuf-lite.txt") ?: return null,
        play = components.filter { it.licence == "Android-SDK-License" }.map { it.module },
        playNotices = blocks("play-services.txt") ?: return null,
    )
}

/**
 * The open-source notices, as the phone shows them: each notice the licences
 * ask to travel with the app, in full, on the watch that carries the app.
 */
@Composable
fun Notices() {
    val context = LocalContext.current
    val notices by produceState<WatchNotices?>(null) { value = withContext(Dispatchers.IO) { readWatchNotices(context) } }
    val list = rememberScalingLazyListState()
    ScreenScaffold(scrollState = list) {
        ScalingLazyColumn(state = list, modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            item { ListHeader { Text(stringResource(R.string.notices_title)) } }
            item { Paragraph(stringResource(R.string.notices_intro)) }
            val shown = notices
            if (shown == null) {
                item { Paragraph(stringResource(R.string.notices_reading)) }
                return@ScalingLazyColumn
            }
            item { Title(stringResource(R.string.notices_typefaces)) }
            blocks(shown.typefaces)
            item { Title(stringResource(R.string.notices_apache)) }
            items(shown.apache) { Module(it) }
            blocks(shown.apacheNotice)
            item { Title(stringResource(R.string.notices_protobuf)) }
            blocks(shown.protobuf)
            item { Title(stringResource(R.string.notices_play)) }
            item { Paragraph(stringResource(R.string.notices_play_body)) }
            items(shown.play) { Module(it) }
            blocks(shown.playNotices)
        }
    }
}

private fun ScalingLazyListScope.blocks(blocks: List<Block>) {
    items(blocks) { block ->
        when (block) {
            is Block.Heading -> Text(block.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = inkDark, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
            is Block.Paragraph -> Paragraph(block.text)
            Block.Rule -> Text("", modifier = Modifier.padding(vertical = 2.dp))
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = inkDark, modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp))
}

@Composable
private fun Paragraph(text: String) {
    Text(text, fontSize = 12.sp, color = inkMidDark, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
}

@Composable
private fun Module(module: String) {
    Text(module, style = CodeStyle.copy(fontSize = 10.sp), color = inkSoftDark, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
}
