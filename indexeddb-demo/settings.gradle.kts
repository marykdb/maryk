pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "maryk-indexeddb-demo"

includeBuild("..") {
    dependencySubstitution {
        substitute(module("io.maryk:maryk-indexeddb")).using(project(":store:indexeddb"))
        substitute(module("io.maryk:maryk-testmodels")).using(project(":testmodels"))
    }
}
