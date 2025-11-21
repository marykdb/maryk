package maryk.conventions

plugins {
    id("maryk.conventions.kotlin-multiplatform-base")
    id("com.android.library")
}

kotlin {
    jvmToolchain(21)
    androidTarget {
        publishLibraryVariants()
        publishLibraryVariantsGroupedByFlavor = true
    }
}

android {
    namespace = "io.maryk"
    compileSdk = 34
    defaultConfig {
        minSdk = 21
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
