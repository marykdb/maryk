import org.gradle.api.initialization.resolve.RepositoriesMode
import org.gradle.api.GradleException
import org.gradle.api.artifacts.repositories.IvyArtifactRepository

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
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenCentral()
        google()
        ivy {
            name = "Node distributions"
            url = uri("https://nodejs.org/dist")
            patternLayout {
                artifact("v[revision]/[artifact]-v[revision]-[classifier].[ext]")
            }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
    }
}

gradle.beforeProject {
    repositories.configureEach {
        val isKotlinNodeDistribution = this is IvyArtifactRepository &&
            url.toString() == "https://nodejs.org/dist"
        if (!isKotlinNodeDistribution) {
            throw GradleException("Project repositories are not permitted: $name")
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
