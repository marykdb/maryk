package maryk.conventions

/** conventions for a limited Kotlin/Native subproject */

plugins {
    id("maryk.conventions.kotlin-multiplatform-base")
}

kotlin {
    linuxX64()
    linuxArm64()

    iosArm64()
    iosSimulatorArm64()
    macosArm64()
    macosX64()
}
