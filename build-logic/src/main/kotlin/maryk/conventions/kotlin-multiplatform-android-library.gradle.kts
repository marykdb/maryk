package maryk.conventions

plugins {
    id("maryk.conventions.kotlin-multiplatform-base")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    jvmToolchain(21)
    android {
        namespace = "io.maryk"
        compileSdk = 36
        minSdk = 21
        withHostTest {}
        withDeviceTest {}
    }
}
