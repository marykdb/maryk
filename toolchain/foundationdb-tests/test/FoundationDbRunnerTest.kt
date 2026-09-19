package maryk.build

import java.io.File
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.util.concurrent.TimeUnit

class FoundationDbRunnerTest {
    @Test
    fun successfulTestsCleanUpAndReleaseLease() = fixture { root ->
        testFoundationDb(root, "jvm")
        assertEquals(listOf("install", "stop", "start", "test", "stop"), root.resolve("events").readLines())
        assertClean(root)
    }

    @Test
    fun failedTestsStillCleanUpAndReleaseLease() = fixture(testExit = 7) { root ->
        assertFailsWith<IllegalStateException> { testFoundationDb(root, "jvm") }
        assertEquals(listOf("install", "stop", "start", "test", "stop"), root.resolve("events").readLines())
        assertClean(root)
    }

    @Test
    fun failedStartupStillCleansUp() = fixture(startExit = 9) { root ->
        assertFailsWith<IllegalStateException> { testFoundationDb(root, "jvm") }
        assertEquals(listOf("install", "stop", "start", "stop"), root.resolve("events").readLines())
        assertClean(root)
    }

    @Test
    fun heldLeasePreventsResetOrStartup() = fixture { root ->
        val state = root.resolve("build/testdatastore").apply { createDirectories() }
        FileChannel.open(state.resolve("fdbserver.lock"), CREATE, WRITE).use { channel ->
            channel.lock().use {
                assertFailsWith<OverlappingFileLockException> { testFoundationDb(root, "jvm") }
                assertFalse(root.resolve("events").exists())
            }
        }
    }

    @Test
    fun terminatedRunnerStopsItsServerAndReleasesLease() = fixture(blockTests = true) { root ->
        val probe = root.resolve("Probe.java")
        probe.writeText("""
            import java.nio.file.Path;
            class Probe {
                public static void main(String[] args) throws Exception {
                    Class.forName("maryk.build.FoundationdbKt")
                        .getMethod("testFoundationDb", Path.class, String.class)
                        .invoke(null, Path.of(args[0]), "jvm");
                }
            }
        """.trimIndent())
        val classpath = listOf(Class.forName("maryk.build.FoundationdbKt"), Unit::class.java)
            .joinToString(File.pathSeparator) { Path.of(it.protectionDomain.codeSource.location.toURI()).toString() }
        val process = ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-cp", classpath, probe.toString(), root.toString(),
        ).redirectErrorStream(true).redirectOutput(root.resolve("probe.log").toFile()).start()
        try {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
            while (process.isAlive && System.nanoTime() < deadline) {
                if (root.resolve("events").exists() && "test" in root.resolve("events").readLines()) break
                Thread.sleep(50)
            }
            assertTrue(root.resolve("events").exists(), "Runner did not start")
            assertTrue("test" in root.resolve("events").readLines(), "Runner did not reach test execution")
            process.destroy()
            assertTrue(process.waitFor(20, TimeUnit.SECONDS), "Runner did not terminate")
            assertEquals("stop", root.resolve("events").readLines().last())
            assertClean(root)
        } finally {
            if (process.isAlive) {
                process.destroy()
                if (!process.waitFor(20, TimeUnit.SECONDS)) process.destroyForcibly().waitFor()
            }
        }
    }

    private fun assertClean(root: Path) {
        assertFalse(root.resolve("running").exists())
        FileChannel.open(root.resolve("build/testdatastore/fdbserver.lock"), WRITE).use { channel ->
            checkNotNull(channel.tryLock()).use { /* The runner released its OS lock. */ }
        }
    }

    private fun fixture(testExit: Int = 0, startExit: Int = 0, blockTests: Boolean = false, body: (Path) -> Unit) {
        // The managed runner, like the existing server scripts, requires a POSIX host.
        if (System.getProperty("os.name").contains("Windows")) return
        val root = Files.createTempDirectory("maryk-fdb-runner-")
        try {
            val scripts = root.resolve("store/foundationdb/scripts").apply { createDirectories() }
            scripts.resolve("install-foundationdb.sh").writeText("echo install >> events\n")
            scripts.resolve("run-fdb-for-tests.sh").writeText("echo start >> events\ntouch running\nexit $startExit\n")
            scripts.resolve("stop-fdb-for-tests.sh").writeText("echo stop >> events\nrm -f running\n")
            root.resolve("kotlin").apply {
                writeText("#!/bin/sh\necho test >> events\n${if (blockTests) "sleep 60" else "exit $testExit"}\n")
                check(toFile().setExecutable(true))
            }
            body(root)
        } finally {
            check(root.toFile().deleteRecursively())
        }
    }
}
