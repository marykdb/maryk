plugins {
    id("maryk.conventions.kotlin-multiplatform-jvm")
    id("maryk.conventions.kotlin-multiplatform-android-library")
    id("maryk.conventions.kotlin-multiplatform-js")
    id("maryk.conventions.kotlin-multiplatform-native")
    id("maryk.conventions.publishing")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(kotlin("test"))
                api(kotlin("test-common"))
                api(kotlin("test-annotations-common"))

                api(libs.kotlinx.coroutines.core)
            }
        }
        jsMain {
            dependencies {
                api(npm("crypto-browserify", "3.12.0"))
            }
        }
    }
}
