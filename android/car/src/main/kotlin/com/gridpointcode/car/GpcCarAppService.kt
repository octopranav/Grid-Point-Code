package com.gridpointcode.car

import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * The car app, as each app declares it: the phone app for Android Auto, and the
 * car's own app for Android Automotive. Each says only what its car screens
 * get to have, in [places].
 *
 * Only the car hosts Google signs may show it, as the library's own list names
 * them; a debug build answers any host, so it can be tried on a test head unit.
 */
abstract class GpcCarAppService : CarAppService() {

    abstract fun places(): CarPlaces

    override fun createHostValidator(): HostValidator =
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext).addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample).build()
        }

    @Deprecated("Deprecated in the library in favour of the SessionInfo overload, which the hosts this app targets still call through")
    override fun onCreateSession(): Session = GpcSession(places())
}

/** One car's session: the places screen first, and a speaker for as long as the car is connected. */
class GpcSession(private val places: CarPlaces) : Session() {

    override fun onCreateScreen(intent: Intent): Screen {
        val speaker = CarSpeaker(carContext)
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) = speaker.close()
        })
        return PlacesScreen(carContext, places, speaker)
    }
}
