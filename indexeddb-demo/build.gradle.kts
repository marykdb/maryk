plugins {
    kotlin("multiplatform") version "2.4.0"
}

group = "io.maryk.demo"
version = "0.1.0"

kotlin {
    js {
        browser {
            commonWebpackConfig {
                cssSupport {
                    enabled.set(true)
                }
                outputFileName = "maryk-indexeddb-demo.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        jsMain {
            dependencies {
                implementation("io.maryk:maryk-indexeddb:0.10.1-SNAPSHOT")
                implementation("io.maryk:maryk-testmodels:0.10.1-SNAPSHOT")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            }
        }
    }
}
