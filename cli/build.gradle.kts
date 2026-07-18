@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.gradle.internal.os.OperatingSystem
import org.gradle.jvm.application.tasks.CreateStartScripts
import org.gradle.api.tasks.bundling.Zip
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable
import org.jetbrains.kotlin.konan.target.Family

plugins {
    id("maryk.conventions.kotlin-multiplatform-jvm")
}

private val foundationDbLibDir = rootProject.projectDir.resolve("store/foundationdb/bin/lib").absolutePath
private val foundationDbLibDirFile = rootProject.projectDir.resolve("store/foundationdb/bin/lib")
private val os = OperatingSystem.current()
private val scriptsDir = rootProject.projectDir.resolve("store/foundationdb/scripts")

// Install FoundationDB client locally so native link tasks can find libfdb_c
val installFoundationDB = tasks.register("installFoundationDB", Exec::class) {
    group = "foundationdb"
    description = "Install or link FoundationDB binaries into store/foundationdb/bin"
    if (os.isWindows) {
        commandLine("powershell", "-ExecutionPolicy", "Bypass", "-File", scriptsDir.resolve("install-foundationdb.ps1").absolutePath)
    } else {
        environment("VERBOSE", "1")
        commandLine("bash", scriptsDir.resolve("install-foundationdb.sh").absolutePath)
    }
}

kotlin {
    jvm {
        binaries {
            executable {
                mainClass.set("io.maryk.cli.MarykCliKt")
            }
        }
    }

    listOf(
        linuxX64(),
        macosArm64(),
        macosX64(),
    ).forEach { nativeTarget ->
        nativeTarget.apply {
            val family = nativeTarget.konanTarget.family
            val libExt = when (family) {
                Family.OSX -> "dylib"
                Family.LINUX -> "so"
                else -> null
            }
            val libFile = libExt?.let { foundationDbLibDirFile.resolve("libfdb_c.$it") }

            fun isMacLibArchitectureCompatible(file: File): Boolean {
                if (!os.isMacOsX || !nativeTarget.name.startsWith("macos", ignoreCase = true)) return true

                val expectedArch = when {
                    nativeTarget.name.contains("x64", ignoreCase = true) -> "x86_64"
                    nativeTarget.name.contains("arm64", ignoreCase = true) -> "arm64"
                    else -> return true
                }

                return try {
                    val process = ProcessBuilder("lipo", "-archs", file.absolutePath)
                        .redirectErrorStream(true)
                        .start()
                    val output = process.inputStream.bufferedReader().use { it.readText() }
                    process.waitFor() == 0 && output.trim().split(Regex("\\s+")).contains(expectedArch)
                } catch (_: RuntimeException) {
                    false
                }
            }

            fun isFoundationDbLibraryAvailable(): Boolean {
                val availableFile = libFile?.exists() == true
                if (!availableFile) return false
                if (family != Family.OSX) return true
                return isMacLibArchitectureCompatible(libFile)
            }

            binaries {
                executable {
                    entryPoint = "io.maryk.cli.main"
                    // Ensure libfdb_c is available and linked for native binaries
                    val libOpts = listOf("-L$foundationDbLibDir", "-lfdb_c", "-rpath", foundationDbLibDir)
                    linkTaskProvider.configure {
                        dependsOn(installFoundationDB)
                        linkerOpts(libOpts)
                        onlyIf {
                            val available = isFoundationDbLibraryAvailable()
                            if (!available && libFile != null) {
                                logger.lifecycle("Skipping ${name}: missing or incompatible ${libFile.absolutePath} for target ${nativeTarget.name}.")
                            }
                            available
                        }
                    }
                }
                withType<TestExecutable>().configureEach {
                    // Tests also need libfdb_c available for link + runtime
                    val libOpts = listOf("-L$foundationDbLibDir", "-lfdb_c", "-rpath", foundationDbLibDir)
                    linkTaskProvider.configure {
                        dependsOn(installFoundationDB)
                        linkerOpts(libOpts)
                        onlyIf {
                            val available = isFoundationDbLibraryAvailable()
                            if (!available && libFile != null) {
                                logger.lifecycle("Skipping ${name}: missing or incompatible ${libFile.absolutePath} for target ${nativeTarget.name}.")
                            }
                            available
                        }
                    }
                }
            }
        }
    }


    sourceSets {
        getByName("commonMain") {
            dependencies {
                implementation(libs.kotter)
                implementation(projects.file)
                implementation(projects.generator)
                implementation(projects.store.rocksdb)
                implementation(projects.store.foundationdb)
                implementation(projects.store.remote)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        getByName("commonTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(projects.testmodels)
            }
        }
    }
}

tasks.named<CreateStartScripts>("startScriptsForJvm") {
    applicationName = "maryk"
}

tasks.named<Zip>("jvmDistZip") {
    archiveBaseName.set("maryk-cli")
}
