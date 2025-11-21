package maryk.conventions

import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest

/** conventions for a limited desktop Kotlin/Native subproject */

plugins {
    id("maryk.conventions.kotlin-multiplatform-base")
}

kotlin {
    linuxX64()
    linuxArm64()

    macosArm64()
    macosX64()
}

tasks.withType<KotlinNativeTest>().configureEach {
    environment("KOTLIN_NATIVE_BACKTRACE", "full")
    environment("TZ", "UTC")
    testLogging {
        events("FAILED", "STANDARD_OUT", "STANDARD_ERROR")
        exceptionFormat = TestExceptionFormat.FULL
        showCauses = true
        showExceptions = true
        showStackTraces = true
    }
}
