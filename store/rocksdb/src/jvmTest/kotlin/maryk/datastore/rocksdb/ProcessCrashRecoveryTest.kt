package maryk.datastore.rocksdb

import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import maryk.core.clock.HLC
import maryk.core.query.responses.AtomicStatus
import maryk.datastore.shared.captureSnapshotVersion
import maryk.datastore.shared.executeAtomic
import maryk.datastore.test.AtomicInterruptionEncryptionProvider
import maryk.datastore.test.assertAtomicEncryptionFailureRollsBack
import maryk.datastore.test.assertInterruptedAtomicState
import maryk.datastore.test.completeInterruptedAtomicOnce
import maryk.datastore.test.interruptedAtomicKey
import maryk.datastore.test.interruptedAtomicModels
import maryk.datastore.test.interruptedAtomicRequest
import maryk.datastore.test.interruptedAtomicVersion
import maryk.datastore.test.seedInterruptedAtomicRecords
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class ProcessCrashRecoveryTest {
    @Test
    fun killedWithEarlierAtomicWritesPendingPublishesNothing() = runTest(timeout = 2.minutes) {
        runCrashCase("pending", committed = false)
    }

    @Test
    fun killedAfterCommitReplaysReceiptWithoutRepeatingAllocations() = runTest(timeout = 2.minutes) {
        runCrashCase("committed", committed = true)
    }

    @Test
    fun encryptionFailureAfterEarlierAtomicWritesRollsBack() = runTest {
        val directory = Files.createTempDirectory("maryk-encryption-rollback").toFile()
        val provider = AtomicInterruptionEncryptionProvider()
        val store = RocksDBDataStore.open(relativePath = directory.absolutePath,
            dataModelsById = interruptedAtomicModels, keepAllVersions = true, keepUpdateHistoryIndex = true,
            fieldEncryptionProvider = provider)
        try {
            assertAtomicEncryptionFailureRollsBack(store, provider)
        } finally {
            store.close()
            directory.deleteRecursively()
        }
    }

    private suspend fun runCrashCase(mode: String, committed: Boolean) {
        val directory = Files.createTempDirectory("maryk-process-crash-$mode").toFile()
        val data = File(directory, "store")
        val barrier = File(directory, "barrier")
        val output = File(directory, "child.log")
        var child: Process? = null
        try {
            child = ProcessBuilder(
                File(System.getProperty("java.home"), "bin/java").absolutePath,
                "-Xmx256m", "-cp", testRuntimeClasspath(), ProcessCrashRecoveryChild::class.java.name,
                mode, data.absolutePath, barrier.absolutePath,
            ).redirectErrorStream(true).redirectOutput(output).start()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(40)
            while (!barrier.isFile && child.isAlive && System.nanoTime() < deadline) Thread.sleep(20)
            assertTrue(barrier.isFile, "Child did not reach $mode barrier: ${output.readText()}")
            assertTrue(child.isAlive, "Child must still hold the open datastore at the barrier: ${output.readText()}")
            val signal = barrier.readText()
            assertTrue(signal.startsWith("$mode\t"), signal)
            child.destroyForcibly()
            assertTrue(child.waitFor(15, TimeUnit.SECONDS), "Killed child did not exit")
            assertFalse(child.isAlive)

            val store = RocksDBDataStore.open(relativePath = data.absolutePath,
                dataModelsById = interruptedAtomicModels, keepAllVersions = true, keepUpdateHistoryIndex = true,
                fieldEncryptionProvider = AtomicInterruptionEncryptionProvider())
            try {
                val expectedVersion = signal.substringAfter('\t').toULongOrNull().takeIf { committed }
                // Check the persisted clock before any read, retry or subsequent mutation
                // can teach this process the committed version.
                if (committed) assertTrue(store.captureSnapshotVersion() > assertNotNull(expectedVersion))
                assertInterruptedAtomicState(store, committed)
                completeInterruptedAtomicOnce(store, expectedVersion)
            } finally {
                store.close()
            }
        } finally {
            child?.let {
                if (it.isAlive) {
                    it.destroyForcibly()
                    it.waitFor(15, TimeUnit.SECONDS)
                }
            }
            directory.deleteRecursively()
        }
    }

    private fun testRuntimeClasspath(): String {
        val entries = linkedSetOf<String>()
        entries += System.getProperty("java.class.path").split(File.pathSeparator).filter(String::isNotBlank)
        var loader = Thread.currentThread().contextClassLoader
        while (loader != null) {
            if (loader is URLClassLoader) loader.getURLs().filter { it.protocol == "file" }.forEach {
                entries += File(it.toURI()).absolutePath
            }
            loader = loader.parent
        }
        entries += File(ProcessCrashRecoveryChild::class.java.protectionDomain.codeSource.location.toURI()).absolutePath
        return entries.joinToString(File.pathSeparator)
    }
}

/** Launched only in a disposable child JVM; the parent kills it without running close/finally. */
object ProcessCrashRecoveryChild {
    @JvmStatic
    fun main(args: Array<String>): Unit = runBlocking {
        require(args.size == 3)
        val mode = args[0]
        require(mode == "pending" || mode == "committed")
        val provider = AtomicInterruptionEncryptionProvider()
        val store = RocksDBDataStore.open(relativePath = args[1], dataModelsById = interruptedAtomicModels,
            keepAllVersions = true, keepUpdateHistoryIndex = true, fieldEncryptionProvider = provider)
        // A future replicated creation prevents wall-clock time from hiding a lost
        // atomic watermark after the process restarts.
        val future = HLC(HLC().toPhysicalUnixTime() + 86_400_000uL, 0u).timestamp
        seedInterruptedAtomicRecords(store, future)
        if (mode == "pending") {
            provider.beforeEncrypt = { context ->
                if (context?.recordKey?.contentEquals(interruptedAtomicKey(2).bytes) == true) {
                    publishBarrier(File(args[2]), "pending\tprepared-second-member")
                    awaitCancellation()
                }
            }
            store.executeAtomic(interruptedAtomicRequest())
            error("Pending encryption barrier was not reached")
        } else {
            val response = store.executeAtomic(interruptedAtomicRequest())
            assertEquals(AtomicStatus.Committed, response.status)
            publishBarrier(File(args[2]), "committed\t${interruptedAtomicVersion(response)}")
            awaitCancellation()
        }
    }

    private fun publishBarrier(target: File, value: String) {
        val temporary = File(target.parentFile, "${target.name}.pending")
        temporary.writeText(value)
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }
}
