pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if(requested.id.namespace == "com.android") {
                useModule("com.android.tools.build:gradle:${requested.version}")
            }
            if (requested.id.id == "kotlin-multiplatform") {
                useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
            }
        }
    }

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

fun includeProjects(vararg names: String) {
    for (name in names) {
        include(":$name")
        project(":$name").projectDir = file(name.replace('-', '/'))
    }
}

includeProjects(
    "testlib",
    "lib",
    "json",
    "yaml",
    "core",
    "testmodels",
    "generator",
    "generator-jvmTest",
    "store-test",
    "store-shared",
    "store-memory",
    "store-rocksdb"
)

rootProject.name = "maryk"
