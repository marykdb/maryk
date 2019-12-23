plugins {
    id("kotlin-multiplatform")
}

apply {
    from("../gradle/common.gradle")
    from("../gradle/js.gradle")
    from("../gradle/jvm.gradle")
    from("../gradle/native.gradle")
    from("../gradle/publish.gradle")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":testlib"))
                api(project(":core"))
                api(project(":yaml"))
                api(project(":json"))
                api(project(":lib"))
            }
        }
    }
}
