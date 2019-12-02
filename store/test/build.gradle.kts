plugins {
    kotlin("multiplatform")
}

apply {
    from("../../gradle/common.gradle")
    from("../../gradle/jvm.gradle")
    from("../../gradle/js.gradle")
    from("../../gradle/native.gradle")
}

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

