package maryk.conventions

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

    linuxX64() // not supported by io.maryk.rocksdb:rocksdb-multiplatform
    linuxArm64() // not supported by kotlinx-datetime

    mingwX64() // not supported by io.maryk.rocksdb:rocksdb-multiplatform

    iosX64()
    iosArm64()
    iosSimulatorArm64()
    macosArm64()
    macosX64()
    watchosArm32()
    watchosArm64()
    tvosArm64()
    iosSimulatorArm64()
    watchosSimulatorArm64()
    tvosSimulatorArm64()
    watchosDeviceArm64()

    androidNativeArm32()
    androidNativeArm64()
    androidNativeX86()
    androidNativeX64()
}
