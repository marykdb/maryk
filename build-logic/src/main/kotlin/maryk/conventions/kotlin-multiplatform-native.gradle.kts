package maryk.conventions

import org.gradle.kotlin.dsl.creating
import org.gradle.kotlin.dsl.getting

/** conventions for a Kotlin/Native subproject */

plugins {
    id("maryk.conventions.kotlin-multiplatform-base")
}

kotlin {

    // Native targets all extend commonMain and commonTest
    //
    // common/
    // └── native/
    //     ├── linuxX64
    //     ├── mingwX64
    //     └── darwin/
    //         ├── macosX64
    //         ├── macosArm64
    //         ├── ios/ (shortcut)/
    //         │   ├── iosArm64
    //         │   ├── iosX64
    //         │   └── iosSimulatorArm64
    //         ├── tvos/ (shortcut)/
    //         │   ├── tvosArm64
    //         │   ├── tvosX64
    //         │   └── tvosSimulatorArm64Main
    //         └── watchos/ (shortcut)/
    //             ├── watchosArm32
    //             ├── watchosArm64
    //             ├── watchosX64
    //             └── watchosSimulatorArm64Main
    //
    // More specialised targets are disabled. They can be enabled, if there is demand for them - just make sure
    // to add `dependsOn(nativeMain)` / `dependsOn(nativeTest)` below for any new targets.

    //linuxX64() // not supported by io.maryk.rocksdb:rocksdb-multiplatform
    //linuxArm64() // not supported by kotlinx-datetime

    //mingwX64() // not supported by io.maryk.rocksdb:rocksdb-multiplatform

    ios()     // shortcut that includes: iosArm64, iosX64
    macosArm64()
    macosX64()

    // https://kotlinlang.org/docs/multiplatform-hierarchy.html#target-shortcuts
    //watchos() // watchosArm32, watchosArm64, watchosX64
    //tvos()    // tvosArm64, tvosX64
    //iosSimulatorArm64()
    //watchosSimulatorArm64()
    //tvosSimulatorArm64()
    //androidNativeArm32()
    //androidNativeArm64()
    //androidNativeX86()
    //androidNativeX64()
    //watchosDeviceArm64()

    @Suppress("UNUSED_VARIABLE")
    sourceSets {
        val commonMain by getting {}
        val commonTest by getting {}

        val nativeMain by creating { dependsOn(commonMain) }
        val nativeTest by creating { dependsOn(commonTest) }

        // Linux
        //val linuxX64Main by getting { dependsOn(nativeMain) }
        //val linuxX64Test by getting { dependsOn(nativeTest) }
        //val linuxArm64Main by getting { dependsOn(nativeMain) }
        //val linuxArm64Test by getting { dependsOn(nativeTest) }

        // Windows - MinGW
        //val mingwX64Main by getting { dependsOn(nativeMain) }
        //val mingwX64Test by getting { dependsOn(nativeTest) }

        // Apple - macOS
        val darwinMain by creating { dependsOn(nativeMain) }
        val darwinTest by creating { dependsOn(nativeTest) }

        val iosArm64Main by getting { dependsOn(darwinMain) }
        val iosArm64Test by getting { dependsOn(darwinTest) }
        val iosX64Main by getting { dependsOn(darwinMain) }
        val iosX64Test by getting { dependsOn(darwinTest) }

        val macosArm64Main by getting { dependsOn(darwinMain) }
        val macosArm64Test by getting { dependsOn(darwinTest) }
        val macosX64Main by getting { dependsOn(darwinMain) }
        val macosX64Test by getting { dependsOn(darwinTest) }
    }
}
