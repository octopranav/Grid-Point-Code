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
include(":car")
include(":app")
include(":wear")
include(":automotive")
