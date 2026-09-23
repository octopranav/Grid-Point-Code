package com.gridpointcode

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Where the device thinks it is, from the platform rather than a vendor library.
 *
 * On current Android the fused provider is part of the platform, and it uses
 * whatever the device has: satellites, Wi-Fi, cell towers. Nothing here needs
 * Play services, which keeps the app working on phones that have none.
 */
class DeviceLocation(private val context: Context) {

    private val manager: LocationManager? = context.getSystemService(LocationManager::class.java)

    fun permitted(): Boolean = granted(Manifest.permission.ACCESS_FINE_LOCATION) ||
        granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    fun enabled(): Boolean = manager?.let(LocationManagerCompat::isLocationEnabled) == true

    /** Fixes as they arrive, once a second at most, until collection stops. */
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
        LocationManagerCompat.requestLocationUpdates(
            manager, provider, request, ContextCompat.getMainExecutor(context), listener,
        )
        awaitClose { LocationManagerCompat.removeUpdates(manager, listener) }
    }

    /**
     * The best provider this device has and this app may use. Satellites need the
     * precise permission; a reader who allowed only an approximate location gets
     * the network's answer, and the accuracy shown beside the code says how rough.
     */
    private fun provider(manager: LocationManager): String? {
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            if (granted(Manifest.permission.ACCESS_FINE_LOCATION)) add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }
        return candidates.firstOrNull {
            LocationManagerCompat.hasProvider(manager, it) && manager.isProviderEnabled(it)
        }
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val INTERVAL_MS = 1_000L
    }
}
