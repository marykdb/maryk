@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("maryk.conventions.kotlin-multiplatform-jvm")
}

kotlin {
    jvm {
        binaries {
            executable {
                mainClass.set("io.maryk.cli.MarykCliKt")
            }
        }
    }

    listOf(
        linuxX64(),
        mingwX64(),
        macosArm64(),
        macosX64(),
    ).forEach { nativeTarget ->
        nativeTarget.apply {
            binaries {
                executable {
                    entryPoint = "io.maryk.cli.main"
                }
            }
        }
    }


    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.varabyte.kotter:kotter:_")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
