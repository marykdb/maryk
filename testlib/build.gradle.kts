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
                api(kotlin("stdlib-common"))
                api(kotlin("test-common"))
                api(kotlin("test-annotations-common"))

                api("org.jetbrains.kotlinx:kotlinx-coroutines-core-common:$coroutinesVersion")
            }
        }
        val jvmMain by getting {
            dependencies {
                api(kotlin("stdlib-jdk8"))
                api(kotlin("test"))
                api(kotlin("test-junit"))

                api("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$coroutinesVersion")
            }
        }
        val jsMain by getting {
            dependencies {
                api(kotlin("stdlib-js"))
                api(kotlin("test-js"))

                api("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:$coroutinesVersion")
            }
        }
        val macosMain by getting {
            dependencies {
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core-native:$coroutinesVersion")
            }
        }
        val iosArm64Main by getting  {
            dependencies {
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core-iosarm64:$coroutinesVersion")
            }
        }
        val iosX64Main by getting  {
            dependencies {
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core-iosx64:$coroutinesVersion")
            }
        }
    }
}
