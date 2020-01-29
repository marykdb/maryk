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
                api("co.touchlab:stately:0.9.4")
            }
        }
        commonTest {
            dependencies {
                implementation(project(":testlib"))
            }
        }
    }
}
