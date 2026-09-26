package com.gridpointcode

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import ca.pranavpatel.algo.gridpointcode.design.CodeStyle
import ca.pranavpatel.algo.gridpointcode.design.Space
import com.gridpointcode.notices.Block
import com.gridpointcode.notices.blocksOf
import com.gridpointcode.notices.componentsIn
import com.gridpointcode.notices.readNotice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Everything the page shows, read once from the assets and laid out into blocks
 * there, off the main thread: the Play services notices alone run to half a
 * megabyte.
 */
private class Notices(
    val map: List<Block>,
    val gestures: List<Block>,
    val typefaces: List<Block>,
    val apache: List<String>,
    val apacheNotices: List<Block>,
    val mit: List<String>,
    val mitNotices: List<Block>,
    val play: List<String>,
    val playNotices: List<Block>,
)

private fun readNotices(context: Context): Notices? {
    val components = componentsIn(readNotice(context, "components.txt") ?: return null)
    fun blocks(name: String) = readNotice(context, name)?.let(::blocksOf)
    return Notices(
        map = blocks("maplibre-native-android.md") ?: return null,
        gestures = blocks("maplibre-gestures-android.md") ?: return null,
        typefaces = (blocks("bitter-OFL.txt") ?: return null) + Block.Rule + (blocks("ibm-plex-OFL.txt") ?: return null),
        apache = components.filter { it.licence == "Apache-2.0" }.map { it.module },
        apacheNotices = (blocks("okhttp-public-suffix-list.txt") ?: return null) + Block.Rule + (blocks("apache-2.0.txt") ?: return null),
        mit = components.filter { it.licence == "MIT" }.map { it.module },
        mitNotices = components.filter { it.licence == "MIT" }.mapNotNull { it.notice }.distinct()
            .map { blocks(it) ?: return null }
            .reduceOrNull { all, next -> all + Block.Rule + next }
            .orEmpty(),
        play = components.filter { it.licence == "Android-SDK-License" }.map { it.module },
        playNotices = blocks("play-services.txt") ?: return null,
    )
}

/**
 * The notices, full screen, like the emergency card: a dialog drawn under the
 * system bars that keeps its text clear of them. A list rather than one long
 * column, because the map's notice alone runs to hundreds of paragraphs.
 */
@Composable
fun NoticesPage(onClose: () -> Unit) {
    val context = LocalContext.current
    val notices by produceState<Notices?>(null) { value = withContext(Dispatchers.IO) { readNotices(context) } }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val page = LocalView.current
        val light = MaterialTheme.colorScheme.background.luminance() > 0.5f
        SideEffect {
            (page.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.getInsetsController(window, page).apply {
                    isAppearanceLightStatusBars = light
                    isAppearanceLightNavigationBars = light
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = Space.step4),
                verticalArrangement = Arrangement.spacedBy(Space.step2),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Space.step3)) {
                        Text(
                            stringResource(R.string.notices_title),
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onClose) { Text(stringResource(R.string.emergency_close)) }
                    }
                }
                item { Text(stringResource(R.string.notices_intro), style = MaterialTheme.typography.bodyLarge) }
                val shown = notices ?: return@LazyColumn
                section(R.string.notices_map, shown.map)
                section(R.string.notices_gestures, shown.gestures)
                section(R.string.notices_typefaces, shown.typefaces)
                item { Title(R.string.notices_apache) }
                item { Text(shown.apache.joinToString("\n"), style = CodeStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize)) }
                blocks(shown.apacheNotices)
                item { Title(R.string.notices_mit) }
                item { Text(shown.mit.joinToString("\n"), style = CodeStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize)) }
                blocks(shown.mitNotices)
                item { Title(R.string.notices_play) }
                item { Text(stringResource(R.string.notices_play_body), style = MaterialTheme.typography.bodySmall) }
                item { Text(shown.play.joinToString("\n"), style = CodeStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize)) }
                blocks(shown.playNotices)
                item { Title(R.string.notices_data) }
                item { Text(stringResource(R.string.notices_data_body), style = MaterialTheme.typography.bodySmall) }
                item { Text("", modifier = Modifier.padding(bottom = Space.step4)) }
            }
        }
    }
}

private fun LazyListScope.section(title: Int, content: List<Block>) {
    item { Title(title) }
    blocks(content)
}

private fun LazyListScope.blocks(content: List<Block>) {
    items(content) { block ->
        when (block) {
            is Block.Heading -> Text(block.text, style = MaterialTheme.typography.titleSmall)
            is Block.Paragraph -> Text(block.text, style = MaterialTheme.typography.bodySmall)
            Block.Rule -> HorizontalDivider()
        }
    }
}

@Composable
private fun Title(title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = Space.step4),
    )
}
