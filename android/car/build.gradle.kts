plugins {
    alias(libs.plugins.android.library)
}

// The car's screens, one set for both cars: Android Auto, which the phone app
// projects, and Android Automotive, where an app of its own runs in the car.
// The car draws them from templates, in its own style, so nothing here draws.
android {
    namespace = "com.gridpointcode.car"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            // The palette as the resources design/build-tokens.mjs writes, day and
            // night: the car's accents are the site's, with no Compose or typefaces
            // brought into a car that draws its own text.
            res.directories.add("../../design/generated/android")
        }
    }
}

dependencies {
    implementation(project(":core"))
    api(libs.car.app)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // CarPlaces hands the saved places over as a StateFlow.
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test.junit)
}
