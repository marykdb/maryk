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
                api("org.apache.hbase:hbase-client:_")
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                api(projects.testmodels)
                api(projects.store.test)
                api("org.apache.hbase:hbase-testing-util:_")
                api("org.slf4j:slf4j-simple:_")
                api("org.slf4j:log4j-over-slf4j:_")
                implementation(Testing.junit4)
                implementation(Testing.mockito.core)
            }
        }
    }
}
