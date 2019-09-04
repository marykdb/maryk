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
        commonTest {
            dependencies {
                implementation(project(":testlib"))
            }
        }
    }
}
