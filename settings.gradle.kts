fun includeMultiPlatformProjects(vararg names: String) {
    for (name in names) {
        includeProject(name, "common")
        includeProject(name, "jvm")
        includeProject(name, "js")
    }
}

fun includeProject(name: String, platform: String) {
    include(":$name-$platform")
    project(":$name-$platform").projectDir = file("$name/$platform")
}

includeMultiPlatformProjects(
    "lib",
    "json",
    "yaml",
    "core",
    "test",
    "generator"
)

rootProject.name = "maryk"
