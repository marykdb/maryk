import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.google.protobuf")
    id("maryk.conventions.kotlin-jvm")
}

val protobufVersion = "3.21.2"


protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
//    generatedFilesBaseDir = "$projectDir/gen"
}

//tasks.clean {
//    delete(protobuf.generatedFilesBaseDir)
//}

dependencies {
    api(projects.generator)

    testImplementation(projects.testmodels)
    testImplementation(libs.protobuf.kotlin)
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        allWarningsAsErrors = true
        jvmTarget = "11"
    }
}
