package com.gridpointcode.wear

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels

/**
 * The watch app: one activity, its screens swiped between. The location and
 * the compass run while it is in front and stop when it is not.
 */
class WatchActivity : ComponentActivity() {

    private val model: WatchModel by viewModels()
    private lateinit var speaker: WatchSpeaker

    private val ask = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { model.start() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speaker = WatchSpeaker(this)
        setContent {
            WatchApp(
                model = model,
                speaker = speaker,
                onAllow = { ask.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
            )
        }
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
}
