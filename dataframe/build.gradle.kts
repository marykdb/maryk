plugins {
    id("maryk.conventions.kotlin-multiplatform-jvm")
    id("maryk.conventions.publishing")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(projects.core)
                api(libs.kotlinx.dataframe)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(projects.testmodels)
            }
        }
    }
}
