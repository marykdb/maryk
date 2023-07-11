package maryk.conventions

plugins {
    id("maryk.conventions.kotlin-multiplatform-base")
    id("com.android.library")
}

kotlin {
    jvmToolchain(11)
    android {
        publishAllLibraryVariants()
        publishLibraryVariantsGroupedByFlavor = true
    }
}

android {
    namespace = "io.maryk"
    @Suppress("UnstableApiUsage")
    buildToolsVersion = "32.0.0"
    compileSdk = 32
    defaultConfig {
        minSdk = 21
        multiDexEnabled = true
        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
