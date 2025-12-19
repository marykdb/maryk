package maryk.conventions

import org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3

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
    jvmToolchain(21)

    compilerOptions {
        apiVersion = KOTLIN_2_3
        languageVersion = KOTLIN_2_3
        allWarningsAsErrors = true
        freeCompilerArgs.addAll("-progressive", "-Xconsistent-data-class-copy-visibility")
    }
}
