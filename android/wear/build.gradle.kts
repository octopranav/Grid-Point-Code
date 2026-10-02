plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

// The same four values the phone app is signed with for upload, and the same
// rule: all four, or unsigned. See app/build.gradle.kts.
val upload: Map<String, String?> = mapOf(
    "store" to "gpc.upload.store",
    "storePassword" to "gpc.upload.storePassword",
    "alias" to "gpc.upload.alias",
    "keyPassword" to "gpc.upload.keyPassword",
).mapValues { (_, property) ->
    providers.gradleProperty(property)
        .orElse(providers.environmentVariable(property.uppercase().replace('.', '_')))
        .orNull
}

android {
    namespace = "com.gridpointcode.wear"
    compileSdk = 37

    defaultConfig {
        // The phone's, on purpose: the Wear Data Layer only joins apps with one
        // name and one signing key, and the store lists them as one app.
        applicationId = "com.gridpointcode"
        // Wear OS 3, where watches with Google's services start.
        minSdk = 30
        targetSdk = 37
        // Every artifact of one listing needs its own version code: the watch's
        // are the phone's plus a thousand.
        versionCode = 1001
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        // The watch's screens, tile and complication, drawn and read on the
        // computer, need its strings, fonts and drawables.
        unitTests.isIncludeAndroidResources = true
        // Robolectric's Android 16 reaches into the JDK's own file descriptors.
        unitTests.all { it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED") }
    }

    signingConfigs {
        if (upload.values.all { it != null }) {
            create("upload") {
                storeFile = file(upload.getValue("store")!!)
                storePassword = upload.getValue("storePassword")
                keyAlias = upload.getValue("alias")
                keyPassword = upload.getValue("keyPassword")
            }
        }
    }

    androidResources {
        // The shrunk release keeps only the face the watch draws, IBM Plex Mono,
        // so it leaves Bitter's licence out with Bitter; Plex's stays.
        ignoreAssetsPatterns += "bitter-OFL.txt"
    }

    lint {
        // Backup is off: releases before Android 12 read that from allowBackup,
        // and the extraction rules keep it off from Android 12 on. Lint would
        // have the older backup rules too, which a release with backup off
        // never reads.
        disable += "DataExtractionRules"
    }

    buildTypes {
        release {
            // Shrunk, resources too: the fonts the watch never draws, Bitter and
            // Plex Sans, go with the part of the design system that sets them.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("upload")
        }
    }
}

dependencies {
    implementation(project(":core"))
    // The palette and the bundled typefaces; the watch draws its own screens.
    implementation(project(":designsystem"))
    implementation(project(":notices"))

    implementation(platform(libs.compose.bom))
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.play.services.wearable)
    implementation(libs.androidx.fragment)
    implementation(libs.wear.tiles)
    implementation(libs.wear.protolayout)
    implementation(libs.wear.protolayout.material3)
    implementation(libs.wear.protolayout.expression)
    implementation(libs.wear.complications.data.source.ktx)
    implementation(libs.androidx.concurrent.futures)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.wear.tiles.testing)
    // The renderer the watch draws tiles with, for the tile's reference images.
    // In the debug build rather than the tests alone, because a library's own
    // resources are made only for a build it is part of; the release never has it.
    debugImplementation(libs.wear.tiles.renderer)
    debugImplementation(libs.compose.ui.test.manifest)
}

// Holds the watch's notices to what its release really ships.
apply(from = rootProject.file("gradle/notices.gradle.kts"))

// The watch's reference images, beside the phone's: its screens on a round
// face and its tile as the watch draws it. ./gradlew :wear:recordRoborazziDebug
// draws them again; :wear:verifyRoborazziDebug, which CI runs, compares.
roborazzi {
    outputDir.set(file("src/test/screenshots"))
}
