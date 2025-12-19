package maryk.conventions

plugins {
    id("maryk.conventions.kotlin-multiplatform-base")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    jvmToolchain(21)
    androidLibrary {
        namespace = "io.maryk"
        compileSdk = 34
        minSdk = 21
    }
}
