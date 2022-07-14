plugins {
    kotlin("multiplatform")
}

apply {
    from("../../gradle/publish.gradle")
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
