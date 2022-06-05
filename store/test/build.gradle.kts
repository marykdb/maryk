plugins {
    kotlin("multiplatform")
}

apply {
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
                api(project(":testmodels"))
            }
        }
    }
}
