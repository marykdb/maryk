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
                api(project(":core"))
                api(project(":yaml"))
            }
        }
        commonTest {
            dependencies {
                implementation(project(":testmodels"))
            }
        }
    }
}
