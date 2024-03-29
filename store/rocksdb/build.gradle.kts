import org.gradle.kotlin.dsl.support.serviceOf

plugins {
    id("maryk.conventions.kotlin-multiplatform-jvm")
    id("maryk.conventions.publishing")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api("org.rocksdb:rocksdbjni:_")

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

tasks.withType<Test>().configureEach {
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
