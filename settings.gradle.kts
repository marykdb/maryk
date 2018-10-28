pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "kotlin-multiplatform") {
                useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
            }
        }
    }

    repositories {
//        maven { setUrl("http://dl.bintray.com/kotlin/kotlin-eap") }

        mavenCentral()

        maven { setUrl("https://plugins.gradle.org/m2/") }
    }
}

fun includeProjects(vararg names: String) {
    for (name in names) {
        include(":$name")
        project(":$name").projectDir = file("$name")
    }
}

includeProjects(
    "test",
    "lib",
    "json",
    "yaml",
    "core",
    "generator"
)

include(":generator-jvmTest")
project(":generator-jvmTest").projectDir = file("generator/jvmTest")

rootProject.name = "maryk"

enableFeaturePreview("GRADLE_METADATA")
