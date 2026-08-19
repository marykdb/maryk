import org.gradle.api.initialization.resolve.RepositoriesMode
import org.gradle.api.GradleException
import org.gradle.api.artifacts.repositories.IvyArtifactRepository

private val kotlinDistributionRepositoryUrls = setOf(
    "https://nodejs.org/dist",
    "https://github.com/yarnpkg/yarn/releases/download",
)

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
        ivy {
            name = "Yarn distributions"
            url = uri("https://github.com/yarnpkg/yarn/releases/download")
            patternLayout {
                artifact("v[revision]/[artifact](-v[revision]).[ext]")
            }
            metadataSources { artifact() }
            content { includeModule("com.yarnpkg", "yarn") }
        }
    }
}

gradle.beforeProject {
    repositories.configureEach {
        val isKotlinDistribution = this is IvyArtifactRepository &&
            url.toString() in kotlinDistributionRepositoryUrls
        if (!isKotlinDistribution) {
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
