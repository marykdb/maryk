plugins {
    id("maryk.conventions.kotlin-multiplatform-jvm")
    id("maryk.conventions.kotlin-multiplatform-js")
    id("maryk.conventions.kotlin-multiplatform-native")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(projects.core)
                api(projects.store.shared)
                api(projects.testmodels)
                api(KotlinX.coroutines.test)
                api(KotlinX.datetime)
                api(Kotlin.test)
            }
        }
    }
}
