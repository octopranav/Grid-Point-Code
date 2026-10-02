plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.baselineprofile)
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

// Where the place-name packs come from: the release the Landmarks workflow
// publishes, unless a build says otherwise with -Pgpc.packs=<url>, as the map's
// styles can be pointed at a local copy with -Pgpc.styles.
val packs: String = providers.gradleProperty("gpc.packs")
    .getOrElse("https://github.com/octopranav/Grid-Point-Code/releases/download/place-packs")

android {
    namespace = "com.gridpointcode"
    compileSdk = 37

    defaultConfig {
        // Permanent once published. It matches the domain the links already use.
        applicationId = "com.gridpointcode"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "PACKS", "\"$packs\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Every test starts from the app as a fresh install leaves it: the
        // orchestrator runs each in its own process and clears the app's data.
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
        // A test that hangs fails after three minutes, with its report, rather
        // than holding up the run: one turned on its side once waited an hour.
        testInstrumentationRunnerArguments["timeout_msec"] = "180000"
    }

    testOptions {
        // The screens drawn on the computer need the app's resources: its
        // strings, fonts, colours and the pseudo-locales.
        unitTests.isIncludeAndroidResources = true
        // Robolectric's Android 16 reaches into the JDK's own file descriptors,
        // which Java 17 and later keep closed unless asked.
        unitTests.all { it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED") }
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
        animationsDisabled = true
        // The emulator the device tests run on, made and booted by Gradle, the
        // same one here and in CI: ./gradlew :app:phoneDebugAndroidTest
        managedDevices {
            allDevices {
                create<com.android.build.api.dsl.ManagedVirtualDevice>("phone") {
                    device = "Pixel 7"
                    apiLevel = 36
                    systemImageSource = "google"
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
        debug {
            // Two made-up languages for testing: en-XA, every word accented and
            // a third longer, for text that will not fit; ar-XB, the English
            // mirrored right to left, for a layout that does not turn round.
            isPseudoLocalesEnabled = true
        }
        release {
            // Shrunk and optimised. No rules of the app's own are needed: the
            // JSON it reads is parsed by hand with the platform's org.json, and
            // the map library brings its own. Resources nothing reaches are
            // dropped too; nothing is looked up by name, and the notices are
            // assets, which are never shrunk.
            isMinifyEnabled = true
            isShrinkResources = true
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
    // Installs the baseline profile shipped in the app on a phone that did not
    // get the app from the store, which would otherwise compile it for it.
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":benchmark"))
    // Saved places to and from the watch.
    implementation(libs.play.services.wearable)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.espresso.intents)
    androidTestImplementation(libs.espresso.accessibility)
    androidTestImplementation(libs.uiautomator)
    androidTestImplementation(libs.kotlin.test.junit)
    androidTestUtil(libs.androidx.test.orchestrator)
    androidTestUtil(libs.androidx.test.services)
    debugImplementation(libs.compose.ui.test.manifest)
    // Watches every activity, view model and view the debug build throws away,
    // and reports any still held. The device tests check after each test.
    debugImplementation(libs.leakcanary.android)
    androidTestImplementation(libs.leakcanary.android.instrumentation)
}

// The baseline profile, written by :app:generateBaselineProfile into
// src/release/generated/baselineProfiles and shipped in the release, so a
// reader's first launch runs code compiled ahead of time rather than
// interpreted. Generated by hand and committed, since it takes minutes on an
// emulator; the release is checked to carry it.
baselineProfile {
    automaticGenerationDuringBuild = false
    saveInSrc = true
}

// The build types the benchmarks install, made by the plugin from the release:
// signed with the debug key, since the upload key never leaves its owner, and
// a release build has to be signed to be installed at all.
android.buildTypes.matching { it.name.startsWith("benchmark") || it.name.startsWith("nonMinified") }.configureEach {
    signingConfig = android.signingConfigs.getByName("debug")
}

// The design's reference images live with the code, where a change that moves
// a screen shows in review beside the change that moved it.
// ./gradlew :app:recordRoborazziDebug draws them again; verifyRoborazziDebug,
// which CI runs, fails on any screen that no longer matches.
roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

// Holds the notices to what the release really ships; see the script.
apply(from = rootProject.file("gradle/notices.gradle.kts"))
