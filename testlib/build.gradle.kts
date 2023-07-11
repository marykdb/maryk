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
                api(kotlin("test"))

                api(KotlinX.coroutines.core)
            }
        }
        jsMain {
            dependencies {
                api(npm("crypto-browserify", "3.12.0"))
            }
        }
    }
}
