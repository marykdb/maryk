@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable

plugins {
    id("maryk.conventions.kotlin-multiplatform-jvm")
}

private val foundationDbLibDir = rootProject.projectDir.resolve("store/foundationdb/bin/lib").absolutePath
private val os = OperatingSystem.current()
private val scriptsDir = rootProject.projectDir.resolve("store/foundationdb/scripts")

// Install FoundationDB client locally so native link tasks can find libfdb_c
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
            binaries {
                executable {
                    entryPoint = "io.maryk.cli.main"
                    // Ensure libfdb_c is available and linked for native binaries
                    val libOpts = listOf("-L$foundationDbLibDir", "-lfdb_c", "-rpath", foundationDbLibDir)
                    linkTaskProvider.configure {
                        dependsOn(installFoundationDB)
                        linkerOpts(libOpts)
                    }
                }
                withType<TestExecutable>().configureEach {
                    // Tests also need libfdb_c available for link + runtime
                    val libOpts = listOf("-L$foundationDbLibDir", "-lfdb_c", "-rpath", foundationDbLibDir)
                    linkTaskProvider.configure {
                        dependsOn(installFoundationDB)
                        linkerOpts(libOpts)
                    }
                }
            }
        }
    }


    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotter)
                implementation(projects.file)
                implementation(projects.generator)
                implementation(projects.store.rocksdb)
                implementation(projects.store.foundationdb)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(projects.testmodels)
            }
        }
    }
}
