import org.gradle.kotlin.dsl.support.serviceOf
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable
import org.jetbrains.kotlin.konan.target.Family

plugins {
    id("maryk.conventions.kotlin-multiplatform-jvm")
    id("maryk.conventions.kotlin-multiplatform-android-library")
    id("maryk.conventions.kotlin-multiplatform-native-limited")
    id("maryk.conventions.publishing")
}

kotlin {
    android {
        enableCoreLibraryDesugaring = true
    }

    sourceSets {
        getByName("commonTest") {
            kotlin.srcDir("src/commonTestExpect/kotlin")
            dependencies {
                implementation(kotlin("test"))
                implementation(projects.testmodels)
                implementation(projects.store.test)
            }
        }

        commonMain {
            dependencies {
                api(libs.maryk.rocksdb.multiplatform)

                api(projects.lib)
                api(projects.core)
                api(projects.store.shared)
                api(projects.file)
            }
        }
        androidHostTest {
            kotlin.srcDir("src/androidUnitTest/kotlin")
        }
        androidDeviceTest {
            kotlin.srcDir("src/commonTest/kotlin")
            dependencies {
                implementation(libs.androidx.test.core)
                implementation(libs.androidx.test.runner)
                implementation(kotlin("test"))
                implementation(projects.testmodels)
                implementation(projects.store.test)
            }
        }
    }
}

dependencies {
    add("coreLibraryDesugaring", libs.android.desugar.jdk.libs)
}

fun Task.configureTestDatabase() {
    val testDatabaseDir = layout.buildDirectory.dir("test-database")

    val fs = serviceOf<FileSystemOperations>()

    doFirst("prepare test-database dir") {
        fs.delete { delete(testDatabaseDir) }
        testDatabaseDir.get().asFile.mkdirs()
    }
    doLast("clean test-database dir") {
        fs.delete { delete(testDatabaseDir) }
    }
}

tasks.withType<Test>().configureEach {
    configureTestDatabase()
}

tasks.matching { it.name == "testAndroidHostTest" }.configureEach {
    // RocksDB tests require platform native libraries; Android host tests run on the build host.
    enabled = false
}

kotlin.targets.withType<KotlinNativeTarget>().configureEach {
    if (konanTarget.family == Family.MINGW) {
        binaries.all {
            linkerOpts("-lrpcrt4")
        }
    }

    binaries.withType<TestExecutable>().all {
        tasks.findByName("${this.target.name}Test")?.apply {
            configureTestDatabase()
        }
    }
}
