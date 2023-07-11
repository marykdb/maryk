package maryk.conventions

import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_8
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/**
 * Base configuration for all Kotlin/Multiplatform conventions.
 *
 * This plugin does not enable any Kotlin target.
 * To enable targets apply a specific Kotlin target convention plugin, e.g.
 *
 * ```
 * plugins {
 *   id("buildsrc.plugins.kmp-js")
 * }
 * ```
 */

plugins {
    id("maryk.conventions.base")
    kotlin("multiplatform")
}


kotlin {
    jvmToolchain(11)

    // configure all Kotlin/JVM Tests to use JUnit Jupiter
    targets.withType<KotlinJvmTarget>().configureEach {
        testRuns.configureEach {
            executionTask.configure {
                useJUnitPlatform()
            }
        }
    }
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JVM_1_8)
        apiVersion.set(KOTLIN_1_8)
        languageVersion.set(KOTLIN_1_8)
        freeCompilerArgs.addAll("-progressive")
        allWarningsAsErrors.set(true)
    }
}
