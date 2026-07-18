@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

fun nativePackageVersionFor(releaseVersionParts: List<Int>): String {
    check(releaseVersionParts.size == 3) {
        "Release version must have three numeric components: ${releaseVersionParts.joinToString(".")}"
    }
    val (major, minor, patch) = releaseVersionParts
    return when {
        major == 0 -> {
            check(patch in 0..9) {
                "Pre-1.0 desktop releases require a single-digit patch version: ${releaseVersionParts.joinToString(".")}"
            }
            "1.0.$minor$patch"
        }
        major == 1 && minor == 0 -> {
            check(patch in 0..9) {
                "Desktop releases before 1.2 require a single-digit patch version: ${releaseVersionParts.joinToString(".")}"
            }
            "1.1.$patch"
        }
        major == 1 && minor == 1 -> {
            check(patch in 0..9) {
                "Desktop releases before 1.2 require a single-digit patch version: ${releaseVersionParts.joinToString(".")}"
            }
            "1.1.1$patch"
        }
        else -> releaseVersionParts.joinToString(".")
    }
}

val releaseVersion = rootProject.version.toString().substringBefore('-')
val releaseVersionParts = releaseVersion.split('.').map(String::toInt)
check(releaseVersionParts.size == 3) {
    "Root release version must have three numeric components: $releaseVersion"
}
val nativePackageVersion = nativePackageVersionFor(releaseVersionParts)

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
        getByName("commonMain") {
            dependencies {
                implementation(projects.core)
                implementation(projects.file)
                implementation(projects.generator)
                implementation(projects.store.rocksdb)
                implementation(projects.store.foundationdb)
                implementation(projects.store.remote)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.compose.material.icons.core)
                implementation(libs.compose.material.icons.extended)
                implementation(libs.kotlinx.datetime)
                implementation(libs.vico.multiplatform.m3)
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.material3)
                implementation(libs.compose.ui)
            }
        }
        getByName("commonTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        getByName("jvmMain") {
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
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Maryk"
            packageVersion = nativePackageVersion
        }
        buildTypes.release.proguard {
            isEnabled.set(true)
            configurationFiles.from(project.file("proguard-rules.pro"))
        }
    }
}

tasks.register("verifyDistributionVersion") {
    group = "verification"
    description = "Checks that desktop and CLI distribution versions derive from the root project version."
    inputs.property("rootVersion", rootProject.version.toString())
    inputs.property("nativePackageVersion", nativePackageVersion)
    inputs.property("releaseTag", providers.gradleProperty("releaseTag").orNull ?: "")
    doLast {
        check(releaseVersion.matches(Regex("""\d+\.\d+\.\d+"""))) {
            "Root release version must start with three numeric components: $releaseVersion"
        }
        val nativeVersionCases = mapOf(
            listOf(0, 12, 0) to "1.0.120",
            listOf(0, 12, 1) to "1.0.121",
            listOf(1, 0, 0) to "1.1.0",
            listOf(1, 1, 0) to "1.1.10",
            listOf(1, 2, 0) to "1.2.0",
            listOf(1, 2, 1) to "1.2.1",
        )
        nativeVersionCases.forEach { (version, expectedNativeVersion) ->
            check(nativePackageVersionFor(version) == expectedNativeVersion) {
                "Expected ${version.joinToString(".")} to map to $expectedNativeVersion"
            }
        }
        check(nativePackageVersion == nativePackageVersionFor(releaseVersionParts))
        check(project.version == rootProject.version) {
            "App/CLI version ${project.version} differs from root ${rootProject.version}"
        }
        providers.gradleProperty("releaseTag").orNull?.let { tag ->
            check('-' !in rootProject.version.toString()) {
                "Release version must not be a snapshot: ${rootProject.version}"
            }
            check(tag == "v$releaseVersion") {
                "Release tag $tag must match project version v$releaseVersion"
            }
        }
    }
}
