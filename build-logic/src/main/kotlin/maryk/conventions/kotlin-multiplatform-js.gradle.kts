package maryk.conventions

/** conventions for a Kotlin/JS subproject */

plugins {
    id("maryk.conventions.kotlin-multiplatform-base")
}

kotlin {
    targets {
        js(IR) {
            browser()
            nodejs()
        }
    }
}
