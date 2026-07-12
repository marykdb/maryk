plugins {
    id("maryk.conventions.kotlin-multiplatform-jvm")
    id("maryk.conventions.publishing")
}

kotlin {
    jvm()

    iosArm64()
    iosX64()
    iosSimulatorArm64()
    macosArm64()
    macosX64()
    tvosArm64()
    tvosSimulatorArm64()
    watchosArm64()
    watchosDeviceArm64()
    watchosSimulatorArm64()
    linuxX64()
    linuxArm64()
    androidNativeArm32()
    androidNativeArm64()
    androidNativeX86()
    androidNativeX64()
    mingwX64()

    sourceSets {
        val commonMain = getByName("commonMain")
        getByName("commonTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val posixMain = create("posixMain") {
            dependsOn(commonMain)
        }
        val androidNativeMain = create("androidNativeMain") {
            dependsOn(posixMain)
        }
        val appleMain = create("appleMain") {
            dependsOn(posixMain)
        }
        val linuxMain = create("linuxMain") {
            dependsOn(posixMain)
        }

        getByName("iosArm64Main") { dependsOn(appleMain) }
        getByName("iosX64Main") { dependsOn(appleMain) }
        getByName("iosSimulatorArm64Main") { dependsOn(appleMain) }
        getByName("macosArm64Main") { dependsOn(appleMain) }
        getByName("macosX64Main") { dependsOn(appleMain) }
        getByName("tvosArm64Main") { dependsOn(appleMain) }
        getByName("tvosSimulatorArm64Main") { dependsOn(appleMain) }
        getByName("watchosArm64Main") { dependsOn(appleMain) }
        getByName("watchosDeviceArm64Main") { dependsOn(appleMain) }
        getByName("watchosSimulatorArm64Main") { dependsOn(appleMain) }
        getByName("linuxX64Main") { dependsOn(linuxMain) }
        getByName("linuxArm64Main") { dependsOn(linuxMain) }
        getByName("androidNativeArm32Main") { dependsOn(androidNativeMain) }
        getByName("androidNativeArm64Main") { dependsOn(androidNativeMain) }
        getByName("androidNativeX86Main") { dependsOn(androidNativeMain) }
        getByName("androidNativeX64Main") { dependsOn(androidNativeMain) }
    }
}

// Suppress expect/actual beta warning across all targets
kotlin.targets.all {
    compilations.all {
        compileTaskProvider.configure {
            compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")
        }
    }
}
