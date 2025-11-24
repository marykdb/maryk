plugins {
    id("maryk.conventions.kotlin-multiplatform-jvm")
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
        val commonMain by getting
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val posixMain by creating {
            dependsOn(commonMain)
        }
        val androidNativeMain by creating {
            dependsOn(posixMain)
        }
        val appleMain by creating {
            dependsOn(posixMain)
        }
        val linuxMain by creating {
            dependsOn(posixMain)
        }

        val iosArm64Main by getting { dependsOn(appleMain) }
        val iosX64Main by getting { dependsOn(appleMain) }
        val iosSimulatorArm64Main by getting { dependsOn(appleMain) }
        val macosArm64Main by getting { dependsOn(appleMain) }
        val macosX64Main by getting { dependsOn(appleMain) }
        val tvosArm64Main by getting { dependsOn(appleMain) }
        val tvosSimulatorArm64Main by getting { dependsOn(appleMain) }
        val watchosArm64Main by getting { dependsOn(appleMain) }
        val watchosDeviceArm64Main by getting { dependsOn(appleMain) }
        val watchosSimulatorArm64Main by getting { dependsOn(appleMain) }
        val linuxX64Main by getting { dependsOn(linuxMain) }
        val linuxArm64Main by getting { dependsOn(linuxMain) }
        val androidNativeArm32Main by getting { dependsOn(androidNativeMain) }
        val androidNativeArm64Main by getting { dependsOn(androidNativeMain) }
        val androidNativeX86Main by getting { dependsOn(androidNativeMain) }
        val androidNativeX64Main by getting { dependsOn(androidNativeMain) }
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
