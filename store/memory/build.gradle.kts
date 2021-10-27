plugins {
    kotlin("multiplatform")
}

apply {
    from("../../gradle/common.gradle")
    from("../../gradle/jvm.gradle")
    from("../../gradle/native.gradle")
    from("../../gradle/publish.gradle")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":store-shared"))
            }
        }
        commonTest {
            dependencies {
                api(project(":testmodels"))
                api(project(":store-test"))
            }
        }
    }
}
