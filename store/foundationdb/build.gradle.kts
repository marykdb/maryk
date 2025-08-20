plugins {
    id("maryk.conventions.kotlin-multiplatform-jvm")
    id("maryk.conventions.publishing")
}

kotlin {
    jvm {
        testRuns["test"].executionTask.configure {
            // FoundationDB Java client may need opens depending on reflection
            jvmArgs(
                "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
                "--add-opens", "java.base/java.lang=ALL-UNNAMED",
                "-Djava.library.path=/usr/local/lib"
            )
            environment("DYLD_LIBRARY_PATH" to "/usr/local/lib")
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.lib)
                api(projects.core)
                api(projects.store.shared)
                api("org.foundationdb:fdb-java:_")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                api(projects.testmodels)
                api(projects.store.test)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
