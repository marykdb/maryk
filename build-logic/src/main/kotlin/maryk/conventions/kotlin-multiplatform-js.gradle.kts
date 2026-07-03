package maryk.conventions

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

/** conventions for a Kotlin/JS subproject */

plugins {
    id("maryk.conventions.kotlin-multiplatform-base")
}

@OptIn(ExperimentalWasmDsl::class)
kotlin {
    js {
        browser {
            testTask {
                useKarma {
                    useConfigDirectory(rootProject.file("karma.config.d"))
                    useChromeHeadless()
                }
            }
        }
        nodejs {
            testTask {
                useMocha {
                    timeout = "3m"
                }
            }
        }
    }
    wasmJs {
        browser {
            testTask {
                useKarma {
                    useConfigDirectory(rootProject.file("karma.config.d"))
                    useChromeHeadless()
                }
            }
        }
        nodejs()
        binaries.library()
    }
}
