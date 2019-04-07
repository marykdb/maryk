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
                api(kotlin("stdlib-common"))
                api(kotlin("test-common"))
                api(kotlin("test-annotations-common"))

                api("org.jetbrains.kotlinx:kotlinx-coroutines-core-common:$coroutinesVersion")
            }
        }
        jvm().compilations["main"].defaultSourceSet {
            dependencies {
                api(kotlin("stdlib-jdk8"))
                api(kotlin("test"))
                api(kotlin("test-junit"))
                api(kotlin("reflect"))

                api("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$coroutinesVersion")
            }
        }
        js().compilations["main"].defaultSourceSet {
            dependencies {
                api(kotlin("stdlib-js"))
                api(kotlin("test-js"))

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
