plugins {
    id("maryk.conventions.kotlin-multiplatform-jvm")
    id("maryk.conventions.kotlin-multiplatform-js")
    id("maryk.conventions.kotlin-multiplatform-native")
    id("maryk.conventions.publishing")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(KotlinX.datetime)
            }
        }
        commonTest {
            dependencies {
                implementation(projects.testlib)
            }
        }
        jsMain {
            dependencies {
                api(npm("buffer", "6.0.3"))
                api(npm("stream-browserify", "3.0.0"))
                api(npm("crypto-browserify", "3.12.0"))
                api(npm("safe-buffer", "5.2.1"))
            }
        }
    }
}
