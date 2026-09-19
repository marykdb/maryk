package maryk.build

import org.jetbrains.amper.plugins.ExecutionAvoidance
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.TaskAction
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories

@TaskAction(executionAvoidance = ExecutionAvoidance.Disabled)
fun testFoundationDb(@Input(inferTaskDependency = false) projectRoot: Path, platform: String) {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    check(!os.contains("windows")) { "Managed FoundationDB tests currently require macOS or Linux" }
    val target = if (platform != "host") platform else when {
        os.contains("mac") -> if (arch in setOf("aarch64", "arm64")) "macosArm64" else "macosX64"
        os.contains("linux") -> if (arch in setOf("aarch64", "arm64")) "linuxArm64" else "linuxX64"
        else -> error("Unsupported FoundationDB host: $os/$arch")
    }
    val state = projectRoot.resolve("build/testdatastore").apply { createDirectories() }
    val scripts = projectRoot.resolve("store/foundationdb/scripts")
    val library = projectRoot.resolve("store/foundationdb/bin/lib").toString()
    val environment = mapOf(
        "FDB_CLUSTER_FILE" to projectRoot.resolve("store/foundationdb/fdb.cluster").toString(),
        "DYLD_LIBRARY_PATH" to library,
        "LD_LIBRARY_PATH" to library,
        "TZ" to "UTC",
        "KOTLIN_NATIVE_BACKTRACE" to "full",
    )
    // Same OS file lock as the Gradle runner, held through final cleanup.
    FileChannel.open(state.resolve("fdbserver.lock"), CREATE, WRITE).use { channel ->
        channel.lock().use {
            fun script(name: String, extra: Map<String, String> = emptyMap()) = runProcess(
                projectRoot, listOf("bash", scripts.resolve(name).toString()), environment + extra,
            )
            script("install-foundationdb.sh")
            script("stop-fdb-for-tests.sh", mapOf("FDB_CLEAN_MODE" to "all"))
            val cleanupMonitor = Any()
            var cleaned = false
            val cleanup = AutoCloseable {
                synchronized(cleanupMonitor) {
                    if (!cleaned) {
                        script("stop-fdb-for-tests.sh", mapOf("FDB_CLEAN_MODE" to "data"))
                        cleaned = true
                    }
                }
            }
            val shutdownHook = Thread({ cleanup.close() }, "maryk-fdb-cleanup")
            Runtime.getRuntime().addShutdownHook(shutdownHook)
            try {
                cleanup.use {
                    script("run-fdb-for-tests.sh")
                    runProcess(
                        projectRoot, listOf("./kotlin", "test", "-p", target, "-m", "foundationdb"),
                        environment,
                    )
                }
            } finally {
                removeShutdownHook(shutdownHook)
            }
        }
    }
}

internal fun runProcess(root: Path, command: List<String>, environment: Map<String, String>) {
    val process = ProcessBuilder(command).directory(root.toFile()).inheritIO().apply {
        environment().putAll(environment)
    }.start()
    val shutdownHook = Thread({ terminateProcess(process) }, "maryk-build-child-cleanup")
    try {
        Runtime.getRuntime().addShutdownHook(shutdownHook)
    } catch (_: IllegalStateException) {
        // Server cleanup itself launches a process during JVM shutdown.
    }
    try {
        check(process.waitFor() == 0) { "Command failed: ${command.joinToString(" ")}" }
    } finally {
        terminateProcess(process)
        removeShutdownHook(shutdownHook)
    }
}

private fun removeShutdownHook(hook: Thread) {
    try {
        Runtime.getRuntime().removeShutdownHook(hook)
    } catch (_: IllegalStateException) {
        // JVM shutdown already started; the registered hook is running.
    }
}

private fun terminateProcess(process: Process) {
    if (!process.isAlive) return
    val descendants = process.descendants().toList()
    descendants.forEach { it.destroy() }
    process.destroy()
    if (!process.waitFor(10, TimeUnit.SECONDS)) process.destroyForcibly().waitFor()
    descendants.filter { it.isAlive }.forEach { it.destroyForcibly() }
}
