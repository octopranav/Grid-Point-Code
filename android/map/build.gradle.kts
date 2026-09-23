plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

// Where the map styles come from: the tile provider the website uses, unless a
// build says otherwise with -Pgpc.styles=<url>. That is how the map is tried
// against a local copy on a machine that cannot reach the real host.
val styles: String = providers.gradleProperty("gpc.styles")
    .getOrElse("https://tiles.openfreemap.org/styles/")

android {
    namespace = "com.gridpointcode.map"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        buildConfigField("String", "STYLES", "\"$styles\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":designsystem"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.maplibre)

    testImplementation(libs.kotlin.test.junit)
}
