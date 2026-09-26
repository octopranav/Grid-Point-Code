plugins {
    alias(libs.plugins.android.application)
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
    namespace = "com.gridpointcode.automotive"
    compileSdk = 37

    defaultConfig {
        // The phone's: the store lists the car's app with the phone's and the
        // watch's, under one name.
        applicationId = "com.gridpointcode"
        // The car's app host, which draws the templates, runs on Android 10 and later.
        minSdk = 29
        targetSdk = 36
        // Every artifact of one listing needs its own version code: the car's
        // are the phone's plus two thousand.
        versionCode = 2001
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        // The notices module carries the Play services notices for the phone and
        // the watch; the car's app uses no Play services, so it leaves them out.
        ignoreAssetsPatterns += "play-services.txt"
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
    implementation(project(":car"))
    implementation(project(":notices"))
    implementation(libs.car.app.automotive)
    // The library's activity is a FragmentActivity, which the library keeps to
    // itself; named here, the release's lint can see the activity is one.
    implementation(libs.androidx.fragment)
}

// Holds the car app's notices to what its release really ships.
apply(from = rootProject.file("gradle/notices.gradle.kts"))
