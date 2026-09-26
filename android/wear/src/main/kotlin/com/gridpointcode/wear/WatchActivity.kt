package com.gridpointcode.wear

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

/**
 * The watch app: one activity, its screens swiped between. The location and
 * the compass run while it is in front and stop when it is not. Opened from
 * the tile's Read aloud, it reads the next place it finds.
 */
class WatchActivity : ComponentActivity() {

    private val model: WatchModel by viewModels()
    private lateinit var speaker: WatchSpeaker

    private val ask = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { model.start() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speaker = WatchSpeaker(this)
        if (savedInstanceState == null) asked(intent)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { model.toRead.collect { speaker.sayCode(it) } }
        }
        setContent {
            WatchApp(
                model = model,
                speaker = speaker,
                onAllow = { ask.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        asked(intent)
    }

    override fun onResume() {
        super.onResume()
        model.start()
    }

    override fun onPause() {
        model.stop()
        super.onPause()
    }

    override fun onDestroy() {
        speaker.close()
        super.onDestroy()
    }

    private fun asked(intent: Intent) {
        if (intent.getBooleanExtra(READ, false)) model.readWhenFound()
    }

    companion object {
        /** The extra the tile's Read aloud opens the app with. */
        const val READ = "com.gridpointcode.wear.READ"
    }
}
