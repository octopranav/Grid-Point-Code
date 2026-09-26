plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// The key a release is signed with for upload never enters the repository. It
// is named by four values, set in ~/.gradle/gradle.properties or as environment
// variables; with all four, a release is signed with it, and without them it is
// built unsigned, which is how CI proves the shrunk build still builds.
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
    namespace = "com.gridpointcode"
    compileSdk = 37

    defaultConfig {
        // Permanent once published. It matches the domain the links already use.
        applicationId = "com.gridpointcode"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
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
            // Shrunk and optimised. No rules of the app's own are needed: the
            // JSON it reads is parsed by hand with the platform's org.json, and
            // the map library brings its own.
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("upload")
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":designsystem"))
    implementation(project(":map"))
    implementation(project(":notices"))
    // Android Auto: the car screens, projected from the phone.
    implementation(project(":car"))
    implementation(libs.car.app.projected)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.compose.ui.tooling.preview)
    // Saved places to and from the watch.
    implementation(libs.play.services.wearable)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.kotlin.test.junit)
}

// Holds the notices to what the release really ships; see the script.
apply(from = rootProject.file("gradle/notices.gradle.kts"))
