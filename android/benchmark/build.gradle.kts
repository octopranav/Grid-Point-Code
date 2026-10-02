plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

// Drives the phone app's release build from outside, as a reader does: to
// write the baseline profile of what their first moments run, and to measure
// start-up and frames. ./gradlew :app:generateBaselineProfile writes the
// profile into the app; the benchmarks are run by hand, on a real phone for
// numbers worth keeping (see TESTING.md).
android {
    namespace = "com.gridpointcode.benchmark"
    compileSdk = 37

    defaultConfig {
        // Profiles are collected without root from Android 13; the benchmarks
        // themselves run on anything from Android 9.
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // The same Pixel 7 on Android 16 the device tests use, made by Gradle.
    testOptions.managedDevices.allDevices {
        create<com.android.build.api.dsl.ManagedVirtualDevice>("phone") {
            device = "Pixel 7"
            apiLevel = 36
            systemImageSource = "google"
        }
    }
}

// On the managed emulator; or, with -Pgpc.connectedDevice, on whatever phone or
// emulator is already connected, a real phone included.
baselineProfile {
    if (providers.gradleProperty("gpc.connectedDevice").isPresent) {
        useConnectedDevices = true
    } else {
        managedDevices += "phone"
        useConnectedDevices = false
    }
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
