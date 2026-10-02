package com.gridpointcode

import android.app.Application
import android.os.Build
import android.os.StrictMode
import android.os.strictmode.Violation
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * The debug build's application, there to turn StrictMode on before anything
 * else runs. Work on the main thread that should not be there, a file read as
 * the app opens, a request to the network, and anything left open or still
 * registered when it is thrown away, is reported where it happens, in the log.
 * The device tests read the violations kept here and fail on any that the app's
 * own code caused. Release builds have neither the class nor the policy.
 */
class DebugApplication : Application() {

    override fun onCreate() {
        val thread = StrictMode.ThreadPolicy.Builder()
            .detectDiskReads()
            .detectDiskWrites()
            .detectNetwork()
            .detectCustomSlowCalls()
            .detectResourceMismatches()
            .detectUnbufferedIo()
            .penaltyLog()
        val vm = StrictMode.VmPolicy.Builder()
            .detectLeakedClosableObjects()
            .detectLeakedRegistrationObjects()
            .detectLeakedSqlLiteObjects()
            .detectActivityLeaks()
            .detectFileUriExposure()
            .detectContentUriWithoutPermission()
            .detectCleartextNetwork()
            .penaltyLog()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            vm.detectIncorrectContextUse().detectUnsafeIntentLaunch()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            thread.penaltyListener(reporter) { violations += it }
            vm.penaltyListener(reporter) { violations += it }
        }
        StrictMode.setThreadPolicy(thread.build())
        StrictMode.setVmPolicy(vm.build())
        super.onCreate()
    }

    companion object {
        /** Every violation since the process started or the list was last cleared, newest last. */
        val violations: MutableList<Violation> = CopyOnWriteArrayList()

        private val reporter = Executors.newSingleThreadExecutor()
    }
}
