package maryk.conventions

import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.base
import org.gradle.kotlin.dsl.withType
import java.io.File

/** common config for all subprojects */

plugins {
    base
}

if (project != rootProject) {
    project.version = rootProject.version
    project.group = rootProject.group
}

tasks.withType<AbstractArchiveTask>().configureEach {
    // https://docs.gradle.org/current/userguide/working_with_files.html#sec:reproducible_archives
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.withType<Test>().configureEach {
    System.getProperty("maryk.jfr.filename")?.takeIf { it.isNotBlank() }?.let { jfrFilename ->
        val settings = System.getProperty("maryk.jfr.settings")?.takeIf { it.isNotBlank() } ?: "profile"
        doFirst("prepare jfr output dir") {
            File(jfrFilename).parentFile?.mkdirs()
        }
        jvmArgs("-XX:StartFlightRecording=filename=$jfrFilename,settings=$settings,dumponexit=true")
    }

    // increase logging for all tests
    testLogging {
        events(
            TestLogEvent.FAILED,
            TestLogEvent.SKIPPED,
            TestLogEvent.STANDARD_ERROR,
            TestLogEvent.STANDARD_OUT,
        )
        exceptionFormat = TestExceptionFormat.FULL
        showCauses = true
        showExceptions = true
        showStackTraces = true
        showStandardStreams = true
    }
}
