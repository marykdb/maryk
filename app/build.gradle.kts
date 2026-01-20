@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("maryk.conventions.kotlin-multiplatform-jvm")
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvm {
        binaries {
            executable {
                mainClass.set("io.maryk.app.MarykAppKt")
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(projects.core)
                implementation(projects.file)
                implementation(projects.generator)
                implementation(projects.store.rocksdb)
                implementation(projects.store.foundationdb)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.compose.material.icons.core)
                implementation(libs.compose.material.icons.extended)
                implementation(libs.kotlinx.datetime)
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.datetime)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "io.maryk.app.MarykAppKt"
    }
}
