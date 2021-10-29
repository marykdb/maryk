plugins {
    id("kotlin-multiplatform")
}

apply {
    from("../gradle/common.gradle")
    from("../gradle/js.gradle")
    from("../gradle/jvm.gradle")
    from("../gradle/native.gradle")
    from("../gradle/publish.gradle")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api("org.jetbrains.kotlinx:kotlinx-datetime:0.3.1")
            }
        }
        commonTest {
            dependencies {
                api("org.jetbrains.kotlinx:kotlinx-datetime:0.3.1")
                implementation(project(":testlib"))
            }
        }
        val jsMain by getting {
            dependencies {
                api(npm("buffer", "6.0.3"))
                api(npm("stream-browserify", "3.0.0"))
                api(npm("crypto-browserify", "3.12.0"))
                api(npm("safe-buffer", "5.2.1"))
            }
        }
    }
}
