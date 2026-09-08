import org.gradle.api.tasks.testing.Test

plugins {
    id("maryk.conventions.kotlin-multiplatform-jvm")
    id("maryk.conventions.kotlin-multiplatform-android-library")
    id("maryk.conventions.kotlin-multiplatform-js")
    id("maryk.conventions.kotlin-multiplatform-native")
}

tasks.withType<Test>().configureEach {
    systemProperty(
        "maryk.store.test.commonMain",
        layout.projectDirectory.dir("src/commonMain/kotlin/maryk/datastore/test").asFile.absolutePath,
    )
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(projects.core)
                api(projects.store.shared)
                api(projects.testmodels)
                api(libs.kotlinx.coroutines.test)
                api(libs.kotlinx.datetime)
                api(kotlin("test"))
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
