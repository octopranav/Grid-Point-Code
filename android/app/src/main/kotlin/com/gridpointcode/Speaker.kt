package com.gridpointcode

import android.content.Context
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * Read aloud, through whatever speech engine the device already has, in the
 * voice of the listener's language.
 *
 * Offline engines are the common case on phones, so this works in a tunnel like
 * everything else on the screen. A line asked for before the engine is ready is
 * kept and spoken once it is, rather than dropped: the reader pressed the button
 * once and should hear it once.
 *
 * A line is never read in a voice for another language. German words in an
 * English voice are a string of wrong sounds, which is worse than silence,
 * because silence at least says to read the words out instead.
 */
class Speaker(context: Context) {

    private val started = MutableStateFlow(false)
    private var waiting: Pair<String, Locale>? = null

    /** Whether the engine has started, after which [voiced] has an answer. */
    val ready: StateFlow<Boolean> = started

    private val engine: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        started.value = status == TextToSpeech.SUCCESS
        if (started.value) {
            waiting?.let { (line, language) -> say(line, language) }
            waiting = null
        }
    }

    /** Whether the device has a voice for this language, or null before the engine has started. */
    fun voiced(language: Locale): Boolean? {
        if (!started.value) return null
        return engine.isLanguageAvailable(voiceFor(language)) >= TextToSpeech.LANG_AVAILABLE
    }

    /** Says the line in the language's voice, and says whether it could. */
    fun say(line: String, language: Locale): Boolean {
        if (!started.value) {
            waiting = line to language
            return true
        }
        if (engine.setLanguage(voiceFor(language)) < TextToSpeech.LANG_AVAILABLE) return false
        engine.speak(line, TextToSpeech.QUEUE_FLUSH, null, "gpc")
        return true
    }

    fun close() {
        engine.stop()
        engine.shutdown()
    }

    /** The reader's own accent when the listener shares the reader's language, else the language alone. */
    private fun voiceFor(language: Locale): Locale =
        Locale.getDefault().takeIf { it.language == language.language } ?: language
}
