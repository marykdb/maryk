import groovy.lang.Closure

plugins {
    id("kotlin-multiplatform")
}

apply {
    from("../gradle/common.gradle")
    from("../gradle/js.gradle")
    from("../gradle/jvm.gradle")
    from("../gradle/native.gradle")
}

(extra["setupCommon"] as Closure<*>)()
(extra["setupJVM"] as Closure<*>)()
(extra["setupJS"] as Closure<*>)(false)
(extra["setupNative"] as Closure<*>)()

val coroutinesVersion = rootProject.extra["coroutinesVersion"]

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api("org.jetbrains.kotlin:kotlin-stdlib-common")
                api("org.jetbrains.kotlin:kotlin-test-common")
                api("org.jetbrains.kotlin:kotlin-test-annotations-common")

                api("org.jetbrains.kotlinx:kotlinx-coroutines-core-common:$coroutinesVersion")
            }
        }
        jvm().compilations["main"].defaultSourceSet {
            dependencies {
                api("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
                api("org.jetbrains.kotlin:kotlin-test")
                api("org.jetbrains.kotlin:kotlin-test-junit")
                implementation("org.jetbrains.kotlin:kotlin-reflect")

                api("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$coroutinesVersion")
            }
        }
        js().compilations["main"].defaultSourceSet {
            dependencies {
                api("org.jetbrains.kotlin:kotlin-stdlib-js")
                api("org.jetbrains.kotlin:kotlin-test-js")

                api("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:$coroutinesVersion")
            }
        }
        macosX64("macos").compilations["main"].defaultSourceSet {
            dependencies {
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core-native:$coroutinesVersion")
            }
        }
    }
}
