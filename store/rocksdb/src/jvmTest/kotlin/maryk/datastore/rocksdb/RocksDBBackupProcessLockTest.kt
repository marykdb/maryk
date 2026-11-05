package maryk.datastore.rocksdb

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import maryk.createTestDBFolder
import maryk.deleteFolder
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RocksDBBackupProcessLockTest {
    @Test
    fun ownershipLockExcludesAnotherProcessAndIsReleasedOnClose() = runTest {
        val directory = createTestDBFolder("backup-process-lock")
        val source = File(directory, "LockProbe.java")
        source.writeText("""
            import java.io.RandomAccessFile;
            public class LockProbe {
                public static void main(String[] args) throws Exception {
                    try (var file = new RandomAccessFile(args[0], "rw")) {
                        var lock = file.getChannel().tryLock();
                        if (lock == null) System.exit(2);
                        lock.release();
                    }
                }
            }
        """.trimIndent())
        fun probe(): Int {
            val process = ProcessBuilder(File(System.getProperty("java.home"), "bin/java").path,
                source.path, File(directory, ".maryk-backup.lock").path).redirectErrorStream(true).start()
            try {
                assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Lock probe must finish")
                val output = process.inputStream.bufferedReader().readText()
                check(process.exitValue() == 0 || process.exitValue() == 2) { output }
                return process.exitValue()
            } finally { process.destroyForcibly() }
        }
        val alias = createTestDBFolder("backup-process-lock-alias")
        val repository = RocksDBBackupRepository.open(directory)
        try {
            // When hard links are supported, a rejected alias must not close a descriptor
            // that releases the original owner's process-scoped POSIX lock.
            val linked = runCatching { Files.createLink(File(alias, ".maryk-backup.lock").toPath(),
                File(directory, ".maryk-backup.lock").toPath()) }.isSuccess
            if (linked) assertFailsWith<IllegalStateException> { RocksDBBackupRepository.open(alias) }
            assertEquals(2, probe())
        } finally { repository.close() }
        try { assertEquals(0, probe()) } finally { deleteFolder(alias); deleteFolder(directory) }
    }
    @Test
    fun supplementaryFilesystemPathsFailBeforeJniCanRedirectThem() = runTest {
        val path = createTestDBFolder("backup-path-🧪")
        try {
            assertFailsWith<IllegalArgumentException> { RocksDBBackupRepository.open(path) }
            assertFailsWith<IllegalArgumentException> { createNewBackupDirectory("$path-new") }
            assertTrue(!File("$path-new").exists())
            assertTrue(!File(path, ".maryk-backup.lock").exists())
        } finally { deleteFolder(path) }
    }

}
