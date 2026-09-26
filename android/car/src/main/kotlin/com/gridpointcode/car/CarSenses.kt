package com.gridpointcode.car

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import java.util.Locale
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** The two location permissions, asked for together, as the phone asks them. */
val LOCATION = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

/**
 * Where the car is, from the platform, as the phone and the watch ask it: on
 * Android Auto from the phone, which rides in the car, and on Android
 * Automotive from the car itself. Asked only while a car screen is showing.
 */
class CarFixes(private val context: Context) {

    private val manager: LocationManager? = context.getSystemService(LocationManager::class.java)

    fun permitted(): Boolean = LOCATION.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    /** Whether location is switched on at all, in the settings of whatever is asked: the phone, or the car. */
    fun switchedOn(): Boolean = manager?.let(LocationManagerCompat::isLocationEnabled) ?: false

    @SuppressLint("MissingPermission") // Checked by permitted() before anything is requested.
    fun fixes(): Flow<Location> = callbackFlow {
        val manager = manager
        val provider = manager?.let(::provider)
        if (manager == null || provider == null || !permitted()) {
            close()
            return@callbackFlow
        }
        val request = LocationRequestCompat.Builder(INTERVAL_MS)
            .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
            .setMinUpdateIntervalMillis(INTERVAL_MS / 2)
            .build()
        val listener = LocationListenerCompat { trySend(it) }
        LocationManagerCompat.requestLocationUpdates(manager, provider, request, ContextCompat.getMainExecutor(context), listener)
        awaitClose { LocationManagerCompat.removeUpdates(manager, listener) }
    }

    private fun provider(manager: LocationManager): String? {
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                add(LocationManager.GPS_PROVIDER)
            }
            add(LocationManager.NETWORK_PROVIDER)
        }
        return candidates.firstOrNull { LocationManagerCompat.hasProvider(manager, it) && manager.isProviderEnabled(it) }
    }

    private companion object {
        const val INTERVAL_MS = 3_000L
    }
}

/**
 * Reads aloud through the speech engine, as guidance the car plays over its
 * own audio, lowering whatever is playing while it speaks. Says on the car's
 * screen when it cannot: with no voice for the language, or no engine at all.
 */
class CarSpeaker(private val context: CarContext) {

    private val audio: AudioManager? = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()
    private var engineState = EngineState.STARTING
    private var waiting: Pair<String, Locale>? = null
    private val engine = TextToSpeech(context) { status ->
        engineState = if (status == TextToSpeech.SUCCESS) EngineState.READY else EngineState.NONE
        waiting?.let { (line, language) -> say(line, language) }
        waiting = null
    }

    init {
        engine.setAudioAttributes(attributes)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                audio?.abandonAudioFocusRequest(focus)
            }
            @Deprecated("Deprecated in the platform")
            override fun onError(utteranceId: String?) {
                audio?.abandonAudioFocusRequest(focus)
            }
        })
    }

    /** Says [line] in [language]; a line asked for while the engine starts is said once it has. */
    fun say(line: String, language: Locale) {
        when (engineState) {
            EngineState.STARTING -> waiting = line to language
            EngineState.NONE -> noVoice()
            EngineState.READY ->
                if (engine.setLanguage(language) < TextToSpeech.LANG_AVAILABLE) {
                    noVoice()
                } else {
                    audio?.requestAudioFocus(focus)
                    engine.speak(line, TextToSpeech.QUEUE_FLUSH, null, "gpc")
                }
        }
    }

    fun close() {
        engine.stop()
        engine.shutdown()
        audio?.abandonAudioFocusRequest(focus)
    }

    private fun noVoice() = CarToast.makeText(context, R.string.car_no_voice, CarToast.LENGTH_LONG).show()

    private enum class EngineState { STARTING, READY, NONE }
}
