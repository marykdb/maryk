plugins {
    id("maryk.conventions.kotlin-multiplatform-jvm")
    id("maryk.conventions.publishing")
}

kotlin {
    jvm {
        testRuns["test"].executionTask.configure {
            // FoundationDB Java client may need opens depending on reflection
            val localLib = rootProject.projectDir.resolve("store/foundationdb/bin/lib").absolutePath
            val sysLib = "/usr/local/lib"
            val pathSep = System.getProperty("path.separator")
            jvmArgs(
                "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
                "--add-opens", "java.base/java.lang=ALL-UNNAMED",
                // Include local FoundationDB lib dir first so libfdb_c can be found, then common system dir
                "-Djava.library.path=${localLib}${pathSep}${sysLib}"
            )
            // Also pass platform library path env vars for native lib discovery
            environment(
                mapOf(
                    "DYLD_LIBRARY_PATH" to localLib,
                    "LD_LIBRARY_PATH" to localLib,
                    "FDB_CLUSTER_FILE" to rootProject.projectDir.resolve("store/foundationdb/fdb.cluster").absolutePath,
                )
            )
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

// Utilities to run a local FoundationDB for tests
val os = org.gradle.internal.os.OperatingSystem.current()

val scriptsDir = rootProject.projectDir.resolve("store/foundationdb/scripts")

val installFoundationDB by tasks.registering(Exec::class) {
    group = "foundationdb"
    description = "Install or link FoundationDB binaries into store/foundationdb/bin"
    if (os.isWindows) {
        commandLine("powershell", "-ExecutionPolicy", "Bypass", "-File", scriptsDir.resolve("install-foundationdb.ps1").absolutePath)
    } else {
        environment("VERBOSE", "1")
        commandLine("bash", scriptsDir.resolve("install-foundationdb.sh").absolutePath)
    }
}

val startFoundationDBForTests by tasks.registering(Exec::class) {
    group = "verification"
    description = "Start a local fdbserver for tests"
    dependsOn(installFoundationDB)
    if (!os.isWindows) {
        commandLine("bash", scriptsDir.resolve("run-fdb-for-tests.sh").absolutePath)
    } else {
        // Windows users: run fdbserver manually or add a PS script analog if desired
        commandLine("bash", "-lc", "echo 'Please start FoundationDB on Windows before tests' && exit 0")
    }
}

val stopFoundationDBForTests by tasks.registering(Exec::class) {
    group = "verification"
    description = "Stop the local fdbserver started for tests"
    if (!os.isWindows) {
        commandLine("bash", scriptsDir.resolve("stop-fdb-for-tests.sh").absolutePath)
        isIgnoreExitValue = true
    } else {
        commandLine("bash", "-lc", "true")
    }
}

tasks.named("jvmTest").configure {
    dependsOn(startFoundationDBForTests)
    finalizedBy(stopFoundationDBForTests)
}
