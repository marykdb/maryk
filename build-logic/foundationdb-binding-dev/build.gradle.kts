import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest

plugins {
    id("maryk.conventions.kotlin-multiplatform-jvm")
    id("maryk.conventions.kotlin-multiplatform-native-desktop")
}

group = "io.maryk.foundationdb"
version = libs.versions.marykFoundationdbBindings.get()

val bindingSource = rootProject.file(requireNotNull(providers.gradleProperty("foundationDbBindingsPath").orNull))
require(bindingSource.resolve("src/commonMain/kotlin/maryk/foundationdb/Transaction.kt").isFile) {
    "foundationDbBindingsPath must point to a FoundationDB binding source checkout"
}
val nativeTestLibraryPath = rootProject.file("store/foundationdb/bin/lib").absolutePath
val testClusterFile = rootProject.file("store/foundationdb/fdb.cluster").absolutePath
val bindingHeaders = bindingSource.resolve("build/foundationdb/headers/include")

val configureBindingTestCluster = tasks.register<Exec>("configureBindingTestCluster") {
    dependsOn(":store:foundationdb:resetFoundationDBTestData", ":store:foundationdb:startFoundationDBForTests")
    environment("FDB_CLUSTER_FILE", testClusterFile)
    commandLine(rootProject.file("store/foundationdb/bin/fdbcli"), "--timeout", "15",
        "--exec", "configure tenant_mode=optional_experimental")
    finalizedBy(":store:foundationdb:stopFoundationDBForTests")
}

kotlin {
    compilerOptions {
        allWarningsAsErrors = false
        suppressWarnings = true
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    sourceSets.configureEach {
        kotlin.srcDir(bindingSource.resolve("src/$name/kotlin"))
    }
    sourceSets.commonMain.dependencies {
        api(libs.kotlinx.coroutines.core)
    }
    sourceSets.commonTest.dependencies {
        implementation(kotlin("test"))
    }
    sourceSets.jvmMain.dependencies {
        api("org.foundationdb:fdb-java:${libs.versions.marykFoundationdb.get()}")
    }
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.withType<TestExecutable>().configureEach {
            freeCompilerArgs += listOf("-entry", "maryk.foundationdb.runNativeTests")
            linkerOpts("-L$nativeTestLibraryPath", "-rpath", nativeTestLibraryPath)
            linkTaskProvider.configure { dependsOn(":store:foundationdb:installFoundationDB") }
        }
        compilations.getByName("main").cinterops.create("foundationdb") {
            defFile(bindingSource.resolve("src/nativeInterop/cinterop/foundationdb.def"))
            includeDirs(bindingHeaders)
            compilerOpts("-I${bindingHeaders.absolutePath}", "-DFDB_API_VERSION=730")
        }
    }
}

tasks.withType<Test>().configureEach {
    filter {
        val isolated = providers.gradleProperty("foundationDbBindingJvmTestFilter").orNull
        if (isolated == null) {
            excludeTestsMatching("maryk.foundationdb.LegacyApiVersionTest")
            excludeTestsMatching("maryk.foundationdb.LegacyVersionstampApiTest")
        } else includeTestsMatching(isolated)
    }
    jvmArgs("-Djava.library.path=$nativeTestLibraryPath")
    environment("DYLD_LIBRARY_PATH", nativeTestLibraryPath)
    environment("LD_LIBRARY_PATH", nativeTestLibraryPath)
    environment("FDB_CLUSTER_FILE", testClusterFile)
    dependsOn(configureBindingTestCluster)
    finalizedBy(":store:foundationdb:stopFoundationDBForTests")
}

tasks.withType<KotlinNativeTest>().configureEach {
    environment("DYLD_LIBRARY_PATH", nativeTestLibraryPath)
    environment("LD_LIBRARY_PATH", nativeTestLibraryPath)
    environment("FDB_CLUSTER_FILE", testClusterFile)
    val filter = providers.gradleProperty("foundationDbBindingNativeTestFilter").orNull
        ?: "*-maryk.foundationdb.LegacyApiVersionTest.*:maryk.foundationdb.LegacyVersionstampApiTest.*:maryk.foundationdb.MetricsBenchmarkNativeTest.*"
    args = args + "--ktest_filter=$filter"
    dependsOn(configureBindingTestCluster)
    finalizedBy(":store:foundationdb:stopFoundationDBForTests")
}
