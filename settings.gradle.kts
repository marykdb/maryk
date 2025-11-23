rootProject.name = "maryk"

pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
    includeBuild("build-logic")
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
enableFeaturePreview("STABLE_CONFIGURATION_CACHE")

plugins {
    id("de.fayard.refreshVersions") version "0.60.6"
}

@Suppress("UnstableApiUsage")
refreshVersions {
    rejectVersionIf {
        candidate.stabilityLevel != de.fayard.refreshVersions.core.StabilityLevel.Stable
    }
}

include(
    ":testlib",
    ":lib",
    ":json",
    ":yaml",
    ":core",
    ":dataframe",
    ":testmodels",
    ":generator",
    ":generator:jvmTest",
    ":store:test",
    ":store:shared",
    ":store:memory",
    ":store:rocksdb",
    ":store:foundationdb",
    ":file",
)
