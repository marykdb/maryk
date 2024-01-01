plugins {
    id("maryk.conventions.kotlin-multiplatform-jvm")
    id("maryk.conventions.publishing")
}

kotlin {
    jvm {
        testRuns["test"].executionTask.configure {
            jvmArgs("--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED", "--add-opens", "java.base/java.lang=ALL-UNNAMED")
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(projects.store.shared)
                api("org.apache.hbase:hbase-shaded-client:_")
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.apache.logging.log4j:log4j-core:_")
                implementation("org.apache.logging.log4j:log4j-api:_")
                implementation("org.apache.logging.log4j:log4j-1.2-api:_")
                api(projects.testmodels)
                api(projects.store.test)
                api("org.apache.hbase:hbase-shaded-testing-util:_")
                implementation(Testing.junit4)
                implementation(Testing.mockito.core)
            }
        }
    }
}
