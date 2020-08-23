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

val coroutinesVersion = rootProject.extra["coroutinesVersion"]

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(kotlin("test-common"))
                api(kotlin("test-annotations-common"))

                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
            }
        }
        val jvmMain by getting {
            dependencies {
                api(kotlin("test"))
                api(kotlin("test-junit"))
            }
        }
        val jsMain by getting {
            dependencies {
                api(kotlin("test-js"))
            }
        }
    }
}
