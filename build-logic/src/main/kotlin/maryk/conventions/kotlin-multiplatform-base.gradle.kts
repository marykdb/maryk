package maryk.conventions

import org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

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
    jvmToolchain(17)

    // configure all Kotlin/JVM Tests to use JUnit Jupiter
    targets.withType<KotlinJvmTarget>().configureEach {
        testRuns.configureEach {
            executionTask.configure {
                useJUnitPlatform()
            }
        }
    }

    @Suppress("OPT_IN_USAGE")
    compilerOptions {
        apiVersion = KOTLIN_2_0
        languageVersion = KOTLIN_2_0
        allWarningsAsErrors = true
        freeCompilerArgs.addAll("-progressive", "-Xconsistent-data-class-copy-visibility")
    }
}
