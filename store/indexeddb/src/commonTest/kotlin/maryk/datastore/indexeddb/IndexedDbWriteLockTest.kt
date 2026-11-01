package maryk.datastore.indexeddb

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class IndexedDbWriteLockTest {
    @Test
    fun leaseLossFailsTheWriteAndReleasesOwnership() = runTest {
        var released = 0
        var signalled = 0
        assertFailsWith<IllegalStateException> {
            withIndexedDbLease(
                databaseName = "test", ownerId = "owner",
                tryAcquire = { true }, awaitSignal = { error("Already acquired") },
                renew = { false }, release = { released++ }, signalRelease = { signalled++ },
            ) { owner ->
                assertEquals("owner", owner)
                delay(10_001)
            }
        }
        assertEquals(1, released)
        assertEquals(1, signalled)
    }

    @Test
    fun cancelledWebLockWaitReleasesALateLockWithoutRunningTheWrite() = runTest {
        lateinit var acquired: (() -> Unit) -> Unit
        var written = false
        var released = 0
        val job = async(start = CoroutineStart.UNDISPATCHED) {
            withIndexedDbWebLock(true, { onAcquired, _ -> acquired = onAcquired }) {
                written = true
            }
        }
        job.cancelAndJoin()
        acquired { released++ }
        assertFalse(written)
        assertEquals(1, released)
    }

    @Test
    fun successfulWebLockWriteReleasesOnce() = runTest {
        var released = 0
        val result = withIndexedDbWebLock(true, { onAcquired, _ -> onAcquired { released++ } }) { 42 }
        assertEquals(42, result)
        assertEquals(1, released)
    }

    @Test
    fun cancellingHeldWebLockReleasesOnceDespiteCleanup() = runTest {
        val entered = CompletableDeferred<Unit>()
        var released = 0
        val job = async {
            withIndexedDbWebLock(true, { onAcquired, _ -> onAcquired { released++ } }) {
                entered.complete(Unit)
                awaitCancellation()
            }
        }
        entered.await()
        job.cancelAndJoin()
        assertEquals(1, released)
    }

    @Test
    fun cancelledLeaseTransactionReleasesLateAcquisitionOnce() = runTest {
        lateinit var complete: (Boolean) -> Unit
        var aborted = 0
        var released = 0
        val job = async(start = CoroutineStart.UNDISPATCHED) {
            awaitLeaseTransaction(
                start = { onComplete, _, onCancel ->
                    complete = onComplete
                    onCancel { aborted++ }
                },
                onLateAcquire = { released++ },
            )
        }
        job.cancelAndJoin()
        complete(true)
        assertEquals(1, aborted)
        assertEquals(1, released)
    }
}
