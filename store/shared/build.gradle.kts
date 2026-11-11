plugins {
    id("maryk.conventions.kotlin-multiplatform-jvm")
    id("maryk.conventions.kotlin-multiplatform-android-library")
    id("maryk.conventions.kotlin-multiplatform-js")
    id("maryk.conventions.kotlin-multiplatform-native")
    id("maryk.conventions.publishing")
}

tasks.register<JavaExec>("runCachePerformanceProbe") {
    group = "verification"
    description = "Compare decoded-value cache memory retention and repeated-read costs."
    val compilation = kotlin.targets.getByName("jvm").compilations.getByName("test")
    classpath(compilation.output.allOutputs, configurations.getByName("jvmTestRuntimeClasspath"))
    mainClass.set("maryk.datastore.shared.benchmark.CachePerformanceProbeKt")
    dependsOn(tasks.named("jvmTestClasses"))
    maxHeapSize = "512m"
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(libs.kotlinx.coroutines.core)
                api(projects.lib)
                api(projects.core)
                implementation(libs.cryptography.core)
                implementation(libs.cryptography.provider.optimal)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(projects.testmodels)
                implementation(projects.store.test)
            }
        }
    }
}
