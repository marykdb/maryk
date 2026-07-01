plugins {
    id("maryk.conventions.kotlin-multiplatform-js")
    id("maryk.conventions.publishing")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(projects.lib)
                api(projects.core)
                api(projects.store.shared)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(projects.testmodels)
                implementation(projects.store.test)
            }
        }
        jsTest {
            dependencies {
                implementation(npm("fake-indexeddb", "6.2.4"))
            }
        }
        wasmJsTest {
            dependencies {
                implementation(npm("fake-indexeddb", "6.2.4"))
            }
        }
    }
}
