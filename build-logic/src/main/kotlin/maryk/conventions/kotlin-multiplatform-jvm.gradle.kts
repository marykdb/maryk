package maryk.conventions

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/** conventions for a Kotlin/JVM subproject */

plugins {
    id("maryk.conventions.kotlin-multiplatform-base")
}

kotlin {
    jvm {
        compilations.configureEach {
            compilerOptions.configure {
                jvmTarget.set(JvmTarget.JVM_1_8)
            }
        }
    }
}
