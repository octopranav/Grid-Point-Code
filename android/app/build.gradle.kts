import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult

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

// The notices name every library the release ships, because several of their
// licences require their notice to travel with the app. This holds the list to
// what the release really resolves, so a dependency added, dropped or upgraded
// past the notice copied from it cannot go out unmentioned.
// Looked up when the task runs: the plugin creates the release's
// configurations after this script has been read.
val shipped = provider { configurations.getByName("releaseRuntimeClasspath") }
    .flatMap { it.incoming.resolutionResult.rootComponent }
val components = layout.projectDirectory.file("src/main/assets/licences/components.txt")

tasks.register("checkNotices") {
    group = "verification"
    description = "Fails when the open-source notices and the release's libraries disagree."
    inputs.file(components)
    doLast {
        val seen = mutableSetOf<ResolvedComponentResult>()
        fun walk(component: ResolvedComponentResult) {
            if (!seen.add(component)) return
            component.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { walk(it.selected) }
        }
        walk(shipped.get())
        val versions = seen.mapNotNull { it.id as? ModuleComponentIdentifier }
            .filterNot { it.module.endsWith("-bom") }
            .associate { "${it.group}:${it.module}" to it.version }

        val listed = components.asFile.readLines()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { it.split(Regex("\\s+")) }
        val named = listed.associate { it[0] to it.getOrNull(2)?.substringAfter('@', "")?.ifEmpty { null } }

        val problems = buildList {
            (versions.keys - named.keys).sorted().forEach { add("$it is shipped but not in the notices") }
            (named.keys - versions.keys).sorted().forEach { add("$it is in the notices but no longer shipped") }
            named.forEach { (module, copied) ->
                val resolved = versions[module]
                if (copied != null && resolved != null && copied != resolved) {
                    add("$module is $resolved, but its notice was copied from $copied")
                }
            }
        }
        if (problems.isNotEmpty()) {
            throw GradleException(problems.joinToString("\n", postfix = "\nUpdate app/src/main/assets/licences."))
        }
        logger.lifecycle("${versions.size} libraries shipped, every one in the notices")
    }
}
