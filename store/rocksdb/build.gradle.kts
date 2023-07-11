import org.jetbrains.kotlin.incremental.deleteDirectoryContents

plugins {
    id("maryk.conventions.kotlin-multiplatform-jvm")
    id("maryk.conventions.kotlin-multiplatform-native")
    id("maryk.conventions.publishing")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(libs.rocksdb.multiplatform)

                api(projects.store.shared)
            }
        }
        commonTest {
            dependencies {
                api(projects.testmodels)
                api(projects.store.test)
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    val testDatabaseDir = layout.buildDirectory.dir("test-database")
    inputs.dir(testDatabaseDir)

    doFirst {
        testDatabaseDir.get().asFile.apply {
            mkdirs()
            deleteDirectoryContents()
        }
    }
}
