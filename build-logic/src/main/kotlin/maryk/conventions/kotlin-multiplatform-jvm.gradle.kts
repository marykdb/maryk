package maryk.conventions

import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17

/** conventions for a Kotlin/JVM subproject */

plugins {
    id("maryk.conventions.kotlin-multiplatform-base")
}

kotlin {
    jvm {
        @Suppress("OPT_IN_USAGE")
        compilerOptions {
            jvmTarget = JVM_17
        }
    }
}
