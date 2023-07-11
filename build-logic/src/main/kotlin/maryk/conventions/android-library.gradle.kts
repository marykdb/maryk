package maryk.conventions

plugins {
    id("maryk.conventions.kotlin-multiplatform-base")
    id("com.android.library")
}

kotlin {
    jvmToolchain(17) // Android 8 requires JDK 17
    android {
        publishAllLibraryVariants()
        publishLibraryVariantsGroupedByFlavor = true
    }
}

android {
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
