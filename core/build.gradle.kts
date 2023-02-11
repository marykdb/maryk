plugins {
    id("kotlin-multiplatform")
}

apply {
    from("../gradle/publish.gradle")
}

kotlin {

    jvm() {
        compilations.all {
            kotlinOptions.freeCompilerArgs += "-Xcontext-receivers"
        }
    }

    js(IR) {
        browser {}
        nodejs {}
        compilations.all {
            kotlinOptions.freeCompilerArgs += "-Xcontext-receivers"
        }
    }

    ios() {
        compilations.all {
            kotlinOptions.freeCompilerArgs += "-Xcontext-receivers"
        }
    }
    macosX64() {
        compilations.all {
            kotlinOptions.freeCompilerArgs += "-Xcontext-receivers"
        }
    }
    macosArm64() {
        compilations.all {
            kotlinOptions.freeCompilerArgs += "-Xcontext-receivers"
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":yaml"))
                api("org.jetbrains.kotlinx:atomicfu:_")
            }
        }
        commonTest {
            dependencies {
                implementation(project(":testmodels"))
            }
        }
    }
}
