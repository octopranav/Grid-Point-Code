plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
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
        targetSdk = 36
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

    buildTypes {
        release {
            isMinifyEnabled = true
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
}

// Holds the watch's notices to what its release really ships.
apply(from = rootProject.file("gradle/notices.gradle.kts"))
