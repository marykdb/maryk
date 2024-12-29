package maryk.conventions

import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

/** conventions for a Kotlin/JVM subproject */

plugins {
    id("maryk.conventions.kotlin-multiplatform-base")
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget = JVM_17
        }
    }
    // configure all Kotlin/JVM Tests to use JUnit Jupiter
    targets.withType<KotlinJvmTarget>().configureEach {
        testRuns.configureEach {
            executionTask.configure {
                useJUnitPlatform()
            }
        }
    }
}
