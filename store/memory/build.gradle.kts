plugins {
    kotlin("multiplatform")
}

apply {
    from("../../gradle/common.gradle")
    from("../../gradle/jvm.gradle")
}

val coroutinesVersion = rootProject.extra["coroutinesVersion"]

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core-common:$coroutinesVersion")

                api(project(":store-shared"))
            }
        }
        commonTest {
            dependencies {
                api(project(":testmodels"))
                api(project(":store-test"))
            }
        }
        jvm().compilations["main"].defaultSourceSet {
            dependencies {
                api("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$coroutinesVersion")
            }
        }
    }
}

