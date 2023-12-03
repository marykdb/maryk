package maryk.conventions

/** conventions for a Kotlin/JS subproject */

plugins {
    id("maryk.conventions.kotlin-multiplatform-base")
}

kotlin {
    js {
        browser()
        nodejs()
    }
}
