pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "gridpointcode-android"

include(":core")
include(":designsystem")
include(":map")
include(":notices")
include(":app")
include(":wear")
