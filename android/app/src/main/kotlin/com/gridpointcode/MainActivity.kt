package com.gridpointcode

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import ca.pranavpatel.algo.gridpointcode.design.GpcTheme
import com.gridpointcode.core.Source

/** The launcher shortcut's action: straight to the emergency card. */
const val ACTION_EMERGENCY = "com.gridpointcode.action.EMERGENCY"

class MainActivity : ComponentActivity() {

    private val model: PlaceViewModel by viewModels()
    private lateinit var speaker: Speaker

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        speaker = Speaker(this)
        if (savedInstanceState == null) arrive(intent)

        setContent {
            GpcTheme {
                PlaceScreen(model = model, speak = speaker::say)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        arrive(intent)
    }

    override fun onDestroy() {
        speaker.close()
        super.onDestroy()
    }

    /**
     * A place handed over by another app: a link to the site, a geo URI, or a
     * piece of text somebody selected. All three go through the one reader the
     * search field uses, so a broken code gets the same answer however it came.
     */
    private fun arrive(intent: Intent?) {
        when (intent?.action) {
            ACTION_EMERGENCY -> model.openEmergency()
            Intent.ACTION_VIEW -> intent.dataString?.let { model.open(it, Source.LINK) }
            Intent.ACTION_PROCESS_TEXT ->
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.let { model.open(it.toString(), Source.CODE) }
        }
    }
}
