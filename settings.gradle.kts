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

include(
    ":testlib",
    ":lib",
    ":json",
    ":yaml",
    ":core",
    ":file",
    ":testmodels",
    ":generator",
    ":generator:jvmTest",
    ":store:test",
    ":store:shared",
    ":store:indexeddb",
    ":store:memory",
    ":store:remote",
    ":store:rocksdb",
    ":store:foundationdb",
    ":cli",
    ":app",
)
