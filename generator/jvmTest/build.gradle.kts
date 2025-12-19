plugins {
    alias(libs.plugins.protobuf)
    id("maryk.conventions.kotlin-jvm")
}

protobuf {
    protoc {
        artifact = libs.protoc.get().toString()
    }
}

dependencies {
    api(projects.generator)

    testImplementation(projects.testmodels)
    testImplementation(libs.protobuf.kotlin)
    testImplementation(kotlin("test"))
}
