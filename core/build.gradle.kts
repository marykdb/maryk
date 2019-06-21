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
                implementation(project(":testmodels"))
            }
        }
        jvm().compilations["main"].defaultSourceSet {
            dependencies {
                api(kotlin("reflect"))
            }
        }
    }
}
