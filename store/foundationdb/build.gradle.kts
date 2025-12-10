import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family

plugins {
    id("maryk.conventions.kotlin-multiplatform-jvm")
    id("maryk.conventions.kotlin-multiplatform-native-desktop")
    id("maryk.conventions.publishing")
}

private val localFoundationDbLibDir = rootProject.projectDir.resolve("store/foundationdb/bin/lib").absolutePath
private val systemFoundationDbLibDir = "/usr/local/lib"
private val pathSeparator = File.pathSeparator

kotlin {
    jvm {
        testRuns["test"].executionTask.configure {
            // FoundationDB Java client may need opens depending on reflection
            jvmArgs(
                "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
                "--add-opens", "java.base/java.lang=ALL-UNNAMED",
                // Include local FoundationDB lib dir first so libfdb_c can be found, then common system dir
                "-Djava.library.path=${localFoundationDbLibDir}${pathSeparator}${systemFoundationDbLibDir}"
            )
            // Also pass platform library path env vars for native lib discovery
            environment(
                mapOf(
                    "DYLD_LIBRARY_PATH" to localFoundationDbLibDir,
                    "LD_LIBRARY_PATH" to localFoundationDbLibDir,
                    "FDB_CLUSTER_FILE" to rootProject.projectDir.resolve("store/foundationdb/fdb.cluster").absolutePath,
                )
            )
        }
    }

    targets.withType<KotlinNativeTarget>().configureEach {
        val target = this
        val libDir = rootProject.projectDir.resolve("store/foundationdb/bin/lib").absolutePath
        val (libExt, envVar) = when (target.konanTarget.family) {
            Family.OSX -> "dylib" to "DYLD_LIBRARY_PATH"
            Family.LINUX -> "so" to "LD_LIBRARY_PATH"
            else -> null to null
        }

        if (libExt != null && envVar != null) {
            val libFile = rootProject.projectDir.resolve("store/foundationdb/bin/lib/libfdb_c.$libExt")

            binaries.withType<org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable>().configureEach {
                linkerOpts("-L$libDir", "-lfdb_c", "-rpath", libDir)
                linkTaskProvider.configure {
                    dependsOn(installFoundationDB)
                    onlyIf {
                        val available = libFile.exists()
                        if (!available) {
                            logger.lifecycle("Skipping ${name} because ${libFile.name} is absent (FoundationDB native client not installed for ${target.konanTarget.family}).")
                        }
                        available
                    }
                }
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.lib)
                api(projects.core)
                api(projects.store.shared)
                api("io.maryk.foundationdb:foundationdb-multiplatform:_")
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

val kotlinExt = extensions.getByType<KotlinMultiplatformExtension>()

tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest>().configureEach {
    val target = kotlinExt.targets.withType<KotlinNativeTarget>().firstOrNull { nativeTarget ->
        name.startsWith(nativeTarget.name)
    }
    val family = target?.konanTarget?.family
    val libExt = when (family) {
        Family.OSX -> "dylib"
        Family.LINUX -> "so"
        else -> null
    }

    if (libExt != null) {
        val libDir = rootProject.projectDir.resolve("store/foundationdb/bin/lib").absolutePath
        val libFile = rootProject.projectDir.resolve("store/foundationdb/bin/lib/libfdb_c.$libExt")
        environment("DYLD_LIBRARY_PATH", libDir)
        environment("LD_LIBRARY_PATH", libDir)
        environment("FDB_CLUSTER_FILE", rootProject.projectDir.resolve("store/foundationdb/fdb.cluster").absolutePath)
        dependsOn(installFoundationDB, startFoundationDBForTests)
        finalizedBy(stopFoundationDBForTests)

        onlyIf {
            val available = libFile.exists()
            if (!available && family != null) {
                logger.lifecycle("Skipping ${name} because ${libFile.name} is absent (FoundationDB native client not installed for $family).")
            }
            available
        }
    } else {
        onlyIf { false }
    }
}

tasks.register<JavaExec>("runFoundationDBTestRunner") {
    group = "application"
    description = "Seed FoundationDB with test models and keep the process alive for manual testing."
    val jvmTarget = kotlinExt.targets.getByName("jvm")
    val testCompilation = jvmTarget.compilations.getByName("test")
    classpath(
        testCompilation.output.allOutputs,
        configurations.getByName("jvmTestRuntimeClasspath")
    )
    mainClass.set("maryk.datastore.foundationdb.FoundationDbTestRunnerKt")
    dependsOn(tasks.named("jvmTestClasses"), startFoundationDBForTests)
    finalizedBy(stopFoundationDBForTests)
    environment(
        mapOf(
            "DYLD_LIBRARY_PATH" to localFoundationDbLibDir,
            "LD_LIBRARY_PATH" to localFoundationDbLibDir,
            "FDB_CLUSTER_FILE" to rootProject.projectDir.resolve("store/foundationdb/fdb.cluster").absolutePath,
        )
    )
    jvmArgs(
        "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "-Djava.library.path=${localFoundationDbLibDir}${pathSeparator}${systemFoundationDbLibDir}"
    )
}
