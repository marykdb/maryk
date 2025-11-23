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
    linuxX64()
    linuxArm64()
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
        val linuxX64Main by getting { dependsOn(linuxMain) }
        val linuxArm64Main by getting { dependsOn(linuxMain) }
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
