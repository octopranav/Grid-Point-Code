package com.gridpointcode

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Read aloud, through whatever speech engine the device already has.
 *
 * Offline engines are the common case on phones, so this works in a tunnel like
 * everything else on the screen. A line asked for before the engine is ready is
 * kept and spoken once it is, rather than dropped: the reader pressed the button
 * once and should hear it once.
 */
class Speaker(context: Context) {

    private var ready = false
    private var waiting: String? = null

    private val engine: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            engine.language = Locale.getDefault()
            waiting?.let(::say)
            waiting = null
        }
    }

    fun say(line: String) {
        if (!ready) {
            waiting = line
            return
        }
        engine.speak(line, TextToSpeech.QUEUE_FLUSH, null, "gpc")
    }

    fun close() {
        engine.stop()
        engine.shutdown()
    }
}
