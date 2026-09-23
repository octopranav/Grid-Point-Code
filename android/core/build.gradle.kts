import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Built by whichever JDK runs Gradle, for the same bytecode level the Android
// modules target. A toolchain would demand a second JDK on every machine for
// no difference in the output.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Every code, every check character and every correction comes from here.
    // Nothing in this module reimplements any part of the format.
    api(libs.gridpointcode)

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
