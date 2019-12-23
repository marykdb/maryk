plugins {
    kotlin("multiplatform")
}

apply {
    from("../../gradle/common.gradle")
    from("../../gradle/jvm.gradle")
    from("../../gradle/js.gradle")
    from("../../gradle/native.gradle")
    from("../../gradle/publish.gradle")
}

val coroutinesVersion = rootProject.extra["coroutinesVersion"]

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api ("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core-common:$coroutinesVersion")

                api(project(":core"))
            }
        }
        commonTest {
            dependencies {
                api(project(":testmodels"))
                api(project(":store-test"))
            }
        }
        val jvmMain by getting {
            dependencies {
                api("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$coroutinesVersion")
            }
        }
        val jsMain by getting {
            dependencies {
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:$coroutinesVersion")
            }
        }
        val macosMain by getting  {
            dependencies {
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core-macosx64:$coroutinesVersion")
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

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + "-Xuse-experimental=kotlin.Experimental"
    }
}
