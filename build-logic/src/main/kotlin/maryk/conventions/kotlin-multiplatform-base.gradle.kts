package maryk.conventions

import org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_4

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

    // During the toolchain migration both builds consume the same source files.
    if (file("module.yaml").isFile) {
        sourceSets.configureEach {
            val isTest = name.endsWith("Test")
            val platform = name.removeSuffix(if (isTest) "Test" else "Main")
            val qualifier = when (platform) {
                "common" -> ""
                "androidHost" -> "@android"
                else -> "@$platform"
            }
            kotlin.setSrcDirs(listOf("${if (isTest) "test" else "src"}$qualifier"))
            resources.setSrcDirs(listOf("${if (isTest) "testResources" else "resources"}$qualifier"))
        }
    }

    compilerOptions {
        apiVersion = KOTLIN_2_4
        languageVersion = KOTLIN_2_4
        allWarningsAsErrors = true
        freeCompilerArgs.addAll("-progressive", "-Xconsistent-data-class-copy-visibility")
    }
}
