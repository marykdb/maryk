plugins {
    id("com.google.protobuf")
    id("maryk.conventions.kotlin-jvm")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:_"
    }
//    generatedFilesBaseDir = "$projectDir/gen"
}

//tasks.clean {
//    delete(protobuf.generatedFilesBaseDir)
//}

dependencies {
    api(projects.generator)

    testImplementation(projects.testmodels)
    testImplementation("com.google.protobuf:protobuf-kotlin:_")
    testImplementation(kotlin("test"))
}
