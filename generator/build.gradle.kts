plugins {
    id("kotlin-multiplatform")
}

apply {
    from("../gradle/publish.gradle")
}

kotlin {
    jvm()

    js(IR) {
        browser {}
        nodejs {}
    }

    ios()
    macosX64()
    macosArm64()

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
