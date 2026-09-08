package maryk.conventions

import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FoundationDbTestServerLeaseTest {
    @Test
    fun resetTestResultStoreRemovesOnlyDisposableBinaryResults() {
        val resultsDirectory = Files.createTempDirectory("maryk-test-results")
        val binaryDirectory = Files.createDirectories(resultsDirectory.resolve("binary"))
        val report = Files.writeString(resultsDirectory.resolve("report.xml"), "report")
        Files.writeString(binaryDirectory.resolve("results.bin"), "partial")

        resetTestResultStore(binaryDirectory)

        try {
            assertFalse(Files.exists(binaryDirectory))
            assertTrue(Files.exists(report))
        } finally {
            Files.deleteIfExists(report)
            Files.deleteIfExists(resultsDirectory)
        }
    }

    @Test
    fun secondLeaseWaitsUntilFirstLeaseCloses() {
        val lockFile = Files.createTempDirectory("maryk-fdb-lease").resolve("fdbserver.lock")
        val firstLease = FoundationDbTestServerLease(lockFile)
        val secondLease = FoundationDbTestServerLease(lockFile)
        val secondAcquired = CountDownLatch(1)

        firstLease.acquire()
        val waiter = thread {
            secondLease.acquire()
            secondAcquired.countDown()
        }

        try {
            assertFalse(secondAcquired.await(100, TimeUnit.MILLISECONDS))

            firstLease.close()

            assertTrue(secondAcquired.await(5, TimeUnit.SECONDS))
        } finally {
            firstLease.close()
            secondLease.close()
            waiter.join(5_000)
            Files.deleteIfExists(lockFile)
            Files.deleteIfExists(lockFile.parent)
        }
    }
}
