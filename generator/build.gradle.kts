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
                api(projects.lib)
                api(projects.json)
                api(projects.yaml)
                api(projects.core)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(projects.testmodels)
            }
        }
    }
}
