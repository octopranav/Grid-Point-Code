plugins {
    alias(libs.plugins.android.library)
}

// The notices the phone and the watch both ship, and reading them: the list of
// libraries an app's release carries, and a notice laid out in blocks a screen
// can show. The Apache License and the Play services notices are here once,
// since both apps are built on both; each app keeps its own list.
android {
    namespace = "com.gridpointcode.notices"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation(libs.kotlin.test.junit)
}
