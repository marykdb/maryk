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
                api(projects.yaml)
                api(libs.atomicfu)
            }
        }
        commonTest {
            dependencies {
                implementation(projects.testmodels)
            }
        }
    }
}
