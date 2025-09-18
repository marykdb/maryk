@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("maryk.conventions.kotlin-multiplatform-base")
    id("maryk.conventions.publishing")
}

kotlin {
    js {
        browser()
    }
    wasmJs {
        browser()
        binaries.library()
    }

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
                api(projects.testmodels)
                api(projects.store.test)
            }
        }
    }
}
