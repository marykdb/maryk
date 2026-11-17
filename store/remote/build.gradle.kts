import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("maryk.conventions.kotlin-multiplatform-jvm")
    id("maryk.conventions.publishing")
}

kotlin {
    applyDefaultHierarchyTemplate()
    linuxX64()
    linuxArm64()
    macosArm64()
    macosX64()

    targets.withType<KotlinNativeTarget>().configureEach {
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
                api(libs.ktor.client.core)
                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.cio)
            }
        }
        getByName("jvmMain") {
            dependencies {
                implementation(libs.ktor.client.okhttp)
            }
        }
        getByName("macosMain") {
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
        getByName("linuxMain") {
            dependencies {
                implementation(libs.ktor.client.cio)
            }
        }
        getByName("commonTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.ktor.client.cio)
                implementation(projects.sql.conformance)
                implementation(projects.testmodels)
                implementation(projects.store.memory)
                implementation(projects.store.test)
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
