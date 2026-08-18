import org.gradle.api.initialization.resolve.RepositoriesMode

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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
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
    ":generator:gradle-plugin",
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
