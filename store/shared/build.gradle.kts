plugins {
    kotlin("multiplatform")
}

apply {
    from("../../gradle/publish.gradle")
}

val coroutinesVersion = rootProject.extra["coroutinesVersion"]

kotlin {
    jvm()

    js(IR) {
        browser {}
        nodejs {}
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")

                api(project(":core"))
            }
        }
        val commonTest by getting {
            dependencies {
                api(project(":testmodels"))
                api(project(":store-test"))
            }
        }
        val nativeMain by creating {
            dependsOn(commonMain)
        }
        val nativeTest by creating {
            dependsOn(commonTest)
        }
    }

    fun org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget.setupNativeTarget() {
        compilations["main"].apply {
            defaultSourceSet {
                val nativeMain by sourceSets.getting {}
                dependsOn(nativeMain)
            }
        }

        compilations["test"].apply {
            defaultSourceSet {
                val nativeTest by sourceSets.getting {}
                dependsOn(nativeTest)
            }
        }
    }

    ios {
        setupNativeTarget()
    }

    macosX64 {
        setupNativeTarget()
    }

    macosArm64 {
        setupNativeTarget()
    }
}
