plugins {
    id("maryk.conventions.kotlin-multiplatform-jvm")
    id("maryk.conventions.publishing")
}

kotlin {
    applyDefaultHierarchyTemplate()
    linuxX64()
    macosArm64()
    macosX64()

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        compilations.getByName("main").cinterops.create("sshspawn") {
            defFile(project.file("src/nativeInterop/cinterop/sshspawn.def"))
            compilerOpts("-I${project.projectDir}/src/nativeInterop/cinterop")
        }
    }

    sourceSets {
        getByName("commonMain") {
            dependencies {
                api(projects.lib)
                api(projects.core)
                api(projects.store.shared)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.cio)
            }
        }
        getByName("commonTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(projects.testmodels)
                implementation(projects.store.memory)
                implementation(projects.file)
            }
        }
        getByName("jvmTest") {
            dependencies {
                implementation(libs.ktor.client.mock)
            }
        }
    }
}
