package maryk.conventions

import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("maryk.conventions.kotlin-multiplatform-base")
    id("com.android.kotlin.multiplatform.library")
}

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

kotlin {
    jvmToolchain(21)
    android {
        namespace = "io.maryk"
        compileSdk = 36
        minSdk = 21
        withHostTest {}
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }
    sourceSets {
        getByName("androidDeviceTest") {
            dependencies {
                implementation(versionCatalog.findLibrary("androidx-test-core").get())
                implementation(versionCatalog.findLibrary("androidx-test-runner").get())
            }
        }
    }
}
