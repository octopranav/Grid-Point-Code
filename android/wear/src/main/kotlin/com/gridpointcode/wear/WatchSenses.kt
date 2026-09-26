package com.gridpointcode.wear

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import com.gridpointcode.core.aloud
import com.gridpointcode.core.spellingFor
import java.util.Locale
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Where the watch is, from the platform, as the phone asks it: the fused
 * provider where there is one, the satellites where the precise permission
 * allows, the network otherwise. On a watch paired to a phone, the platform
 * may ask the phone.
 */
class WatchLocation(private val context: Context) {

    private val manager: LocationManager? = context.getSystemService(LocationManager::class.java)

    fun permitted(): Boolean = granted(Manifest.permission.ACCESS_FINE_LOCATION) ||
        granted(Manifest.permission.ACCESS_COARSE_LOCATION)

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
            if (granted(Manifest.permission.ACCESS_FINE_LOCATION)) add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }
        return candidates.firstOrNull { LocationManagerCompat.hasProvider(manager, it) && manager.isProviderEnabled(it) }
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val INTERVAL_MS = 2_000L
    }
}

/**
 * Which way the watch faces, in degrees clockwise from north, from the rotation
 * sensor, so an arrow can point where the reader has to go rather than where
 * north is. None on a watch without the sensor, and the arrow then points from
 * north, as the phone's list does.
 */
class WatchCompass(context: Context) {

    private val sensors: SensorManager? = context.getSystemService(SensorManager::class.java)
    private val rotation: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    val available: Boolean get() = rotation != null

    fun headings(): Flow<Float> = callbackFlow {
        val sensors = sensors
        val rotation = rotation
        if (sensors == null || rotation == null) {
            close()
            return@callbackFlow
        }
        val matrix = FloatArray(9)
        val angles = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                SensorManager.getOrientation(matrix, angles)
                trySend(((Math.toDegrees(angles[0].toDouble()) + 360) % 360).toFloat())
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        sensors.registerListener(listener, rotation, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sensors.unregisterListener(listener) }
    }
}

/**
 * Reads aloud through the watch's own speech engine, in the listener's
 * language, and says on the screen when it cannot: with no voice for the
 * language, and with no engine at all, which a watch may well have.
 */
class WatchSpeaker(context: Context) {

    private val app = context.applicationContext
    private var engineState = EngineState.STARTING
    private var waiting: Pair<String, Locale>? = null
    private val engine = TextToSpeech(app) { status ->
        engineState = if (status == TextToSpeech.SUCCESS) EngineState.READY else EngineState.NONE
        waiting?.let { (line, language) -> say(line, language) }
        waiting = null
    }

    /** Says [line] in [language]; a line asked for while the engine starts is said once it has. */
    fun say(line: String, language: Locale) {
        when (engineState) {
            EngineState.STARTING -> waiting = line to language
            EngineState.NONE -> noVoice()
            EngineState.READY ->
                if (engine.setLanguage(language) < TextToSpeech.LANG_AVAILABLE) noVoice()
                else engine.speak(line, TextToSpeech.QUEUE_FLUSH, null, "gpc")
        }
    }

    /** Reads [code] aloud in the words of the listener's language. */
    fun sayCode(code: String) {
        val spelling = spellingFor(Locale.getDefault())
        say(aloud(code, spelling), spelling.locale)
    }

    private fun noVoice() = Toast.makeText(app, R.string.no_voice, Toast.LENGTH_SHORT).show()

    private enum class EngineState { STARTING, READY, NONE }

    fun close() {
        engine.stop()
        engine.shutdown()
    }
}
