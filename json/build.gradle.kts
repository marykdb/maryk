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
(extra["setupJS"] as Closure<*>)()
(extra["setupNative"] as Closure<*>)()

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":lib"))
            }
        }
        commonTest {
            dependencies {
                implementation(project(":testlib"))
            }
        }
    }
}
