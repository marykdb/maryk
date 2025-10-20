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
    sourceSets {
        commonMain {
            dependencies {
                api("io.maryk.rocksdb:rocksdb-multiplatform:_")

                api(projects.lib)
                api(projects.core)
                api(projects.store.shared)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                api(projects.testmodels)
                api(projects.store.test)
            }
        }
    }
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
