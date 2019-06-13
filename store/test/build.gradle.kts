import groovy.lang.Closure

plugins {
    kotlin("multiplatform")
}

apply {
    from("../../gradle/common.gradle")
    from("../../gradle/jvm.gradle")
}

(extra["setupCommon"] as Closure<*>)()
(extra["setupJVM"] as Closure<*>)()

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":store-shared"))
                api(project(":testmodels"))
            }
        }
    }
}

