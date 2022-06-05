plugins {
    id("kotlin-multiplatform")
}

apply {
    from("../gradle/publish.gradle")
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
                api(kotlin("test-common"))
                api(kotlin("test-annotations-common"))

                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
            }
        }
        val commonTest by getting
        val jvmMain by getting {
            dependencies {
                api(kotlin("test"))
                api(kotlin("test-junit"))
            }
        }
        val jsMain by getting {
            dependencies {
                api(kotlin("test-js"))
                api(npm("crypto-browserify", "3.12.0"))
            }
        }
        val darwinMain by creating {
            dependsOn(commonMain)
        }
        val darwinTest by creating {
            dependsOn(commonTest)
        }
    }

    fun org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget.setupAppleTarget() {
        compilations["main"].apply {
            defaultSourceSet {
                val darwinMain by sourceSets.getting {}
                dependsOn(darwinMain)
            }
        }

        compilations["test"].apply {
            defaultSourceSet {
                val darwinTest by sourceSets.getting {}
                dependsOn(darwinTest)
            }
        }
    }

    ios {
        setupAppleTarget()
    }

    macosX64 {
        setupAppleTarget()
    }

    macosArm64 {
        setupAppleTarget()
    }
}
