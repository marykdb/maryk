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
                api(KotlinX.coroutines.core)

                api(projects.core)
            }
        }
        commonTest {
            dependencies {
                api(projects.testmodels)
                api(projects.store.test)
            }
        }
    }
}
