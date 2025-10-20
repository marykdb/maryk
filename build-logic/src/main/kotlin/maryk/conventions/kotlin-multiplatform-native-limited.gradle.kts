package maryk.conventions

import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest

/** conventions for a limited Kotlin/Native subproject */

plugins {
    id("maryk.conventions.kotlin-multiplatform-base")
}

kotlin {
    linuxX64()
    linuxArm64()

    mingwX64()

    iosArm64()
    iosSimulatorArm64()
    macosArm64()
    macosX64()
    watchosArm64()
    watchosDeviceArm64()
    watchosSimulatorArm64()
    tvosArm64()
    tvosSimulatorArm64()

    androidNativeArm32()
    androidNativeArm64()
    androidNativeX86()
    androidNativeX64()
}

tasks.withType<KotlinNativeTest>().configureEach {
    environment("KOTLIN_NATIVE_BACKTRACE", "full")
    environment("TZ", "UTC")
    testLogging {
        events("FAILED", "STANDARD_OUT", "STANDARD_ERROR")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showCauses = true
        showExceptions = true
        showStackTraces = true
    }
}
