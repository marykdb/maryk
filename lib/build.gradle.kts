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

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(KotlinX.datetime)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(project(":testlib"))
            }
        }
        val jsMain by getting {
            dependencies {
                api(npm("buffer", "6.0.3"))
                api(npm("stream-browserify", "3.0.0"))
                api(npm("crypto-browserify", "3.12.0"))
                api(npm("safe-buffer", "5.2.1"))
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
