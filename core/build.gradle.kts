plugins {
    id("kotlin-multiplatform")
}

apply {
    from("../gradle/common.gradle")
    from("../gradle/js.gradle")
    from("../gradle/jvm.gradle")
    from("../gradle/native.gradle")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":yaml"))
            }
        }
        commonTest {
            dependencies {
                implementation(project(":testmodels")) {
                    // Workaround for: https://youtrack.jetbrains.com/issue/KT-33712
                    exclude(module = "core")
                }
            }
        }
        val jvmMain by getting {
            dependencies {
                api(kotlin("reflect"))
            }
        }
    }
}
