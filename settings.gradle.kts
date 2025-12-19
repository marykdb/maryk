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
        maven("https://central.sonatype.com/repository/maven-snapshots/") {
            mavenContent { snapshotsOnly() }
        }
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
    ":dataframe",
    ":testmodels",
    ":generator",
    ":generator:jvmTest",
    ":store:test",
    ":store:shared",
    ":store:memory",
    ":store:rocksdb",
    ":store:foundationdb",
    ":cli",
)
