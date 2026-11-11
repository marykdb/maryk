package maryk.datastore.foundationdb

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.foundationdb.FDBException
import maryk.foundationdb.Transaction
import maryk.foundationdb.failedFdbFuture
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class TrackedTransactionCancellationTest {
    @Test
    fun cancellingTheTrackedFutureClosesItsHandleWithoutCommitting() = runBoundedIntegrationTest {
        val store = openStore()
        val body = CompletableDeferred<String>()
        try {
            val entered = CompletableDeferred<Transaction>()
            val key = testKey(store)
            val future = store.tc.runAsync { transaction ->
                transaction.set(key, byteArrayOf(1))
                entered.complete(transaction)
                CoroutineFdbFuture(body)
            }
            val transaction = withTimeout(5.seconds) { entered.await() }
            future.cancel()
            assertClosed(transaction)
            withTimeout(5.seconds) { while (!body.isCancelled) delay(10.milliseconds) }
            assertTrue(future.isCancelled)
            assertNull(store.tc.run { it.get(key).awaitResult() })
        } finally {
            body.cancel()
            store.close()
        }
    }

    @Test
    fun cancellingTheOwningAwaitClosesItsHandleWithoutCommitting() = runBoundedIntegrationTest {
        val store = openStore()
        val body = CompletableDeferred<String>()
        try {
            val entered = CompletableDeferred<Transaction>()
            val key = testKey(store)
            val future = store.tc.runAsync { transaction ->
                transaction.set(key, byteArrayOf(1))
                entered.complete(transaction)
                CoroutineFdbFuture(body)
            }
            val transaction = withTimeout(5.seconds) { entered.await() }
            val waiter = launch(start = CoroutineStart.UNDISPATCHED) { future.await() }
            withTimeout(5.seconds) { waiter.cancelAndJoin() }
            assertClosed(transaction)
            withTimeout(5.seconds) { while (!body.isCancelled) delay(10.milliseconds) }
            assertTrue(future.isCancelled)
            assertNull(store.tc.run { it.get(key).awaitResult() })
        } finally {
            body.cancel()
            store.close()
        }
    }

    @Test
    fun cancellationAfterRetryClosesEveryAttemptWithoutAnotherRetryOrCommit() = runBoundedIntegrationTest {
        val store = openStore()
        val body = CompletableDeferred<String>()
        val attempts = atomic(0)
        val transactions = atomic<List<Transaction>>(emptyList())
        try {
            val retried = CompletableDeferred<Unit>()
            val key = testKey(store)
            val future = store.tc.runAsync { transaction ->
                transactions.update { it + transaction }
                transaction.set(key, byteArrayOf(1))
                if (attempts.incrementAndGet() == 1) {
                    failedFdbFuture<String>(FDBException("Injected conflict", 1020))
                } else {
                    retried.complete(Unit)
                    CoroutineFdbFuture(body)
                }
            }
            withTimeout(5.seconds) { retried.await() }
            future.cancel()
            transactions.value.forEach { assertClosed(it) }
            withTimeout(5.seconds) { while (!body.isCancelled) delay(10.milliseconds) }
            delay(50.milliseconds)
            assertEquals(2, attempts.value, "Cancellation must not start another retry")
            assertTrue(future.isCancelled)
            assertNull(store.tc.run { it.get(key).awaitResult() })
        } finally {
            body.cancel()
            store.close()
        }
    }

    @Test
    fun cancellingAnObserverPreservesTheTrackedCommitAndClosesAfterSuccess() = runBoundedIntegrationTest {
        val store = openStore()
        val body = CompletableDeferred<String>()
        try {
            val entered = CompletableDeferred<Transaction>()
            val key = testKey(store)
            val future = store.tc.runAsync { transaction ->
                transaction.set(key, byteArrayOf(1))
                entered.complete(transaction)
                CoroutineFdbFuture(body)
            }
            val transaction = withTimeout(5.seconds) { entered.await() }
            val observer = launch(start = CoroutineStart.UNDISPATCHED) { future.awaitObserved() }
            withTimeout(5.seconds) { observer.cancelAndJoin() }
            assertFalse(body.isCancelled)
            assertFalse(future.isDone)
            // A cancelled observer must leave both the operation and transaction usable.
            transaction.getReadVersion().await()
            body.complete("committed")
            assertEquals("committed", withTimeout(5.seconds) { future.await() })
            assertClosed(transaction)
            assertContentEquals(byteArrayOf(1), store.tc.run { it.get(key).awaitResult() })
        } finally {
            body.cancel()
            store.close()
        }
    }

    @Test
    fun datastoreCloseCancelsAnUnobservedTrackedFutureAndClosesItsHandle() = runBoundedIntegrationTest {
        val path = listOf("maryk", "tracked-close", Uuid.random().toString())
        val store = openStore(path)
        val body = CompletableDeferred<String>()
        try {
            val entered = CompletableDeferred<Transaction>()
            val key = testKey(store)
            val future = store.tc.runAsync { transaction ->
                transaction.set(key, byteArrayOf(1))
                entered.complete(transaction)
                CoroutineFdbFuture(body)
            }
            val transaction = withTimeout(5.seconds) { entered.await() }
            // There is deliberately no awaiting coroutine to propagate cancellation.
            withTimeout(7.seconds) { store.close() }
            assertClosed(transaction)
            withTimeout(5.seconds) { while (!body.isCancelled) delay(10.milliseconds) }
            assertTrue(future.isCancelled)
            val reopened = openStore(path)
            try { assertNull(reopened.tc.run { it.get(key).awaitResult() }) }
            finally { reopened.close() }
        } finally {
            body.cancel()
            store.close()
        }
    }

    @Test
    fun cancellingASuspendingAttemptClosesItsHandleWithoutCommitting() = runBoundedIntegrationTest {
        val store = openStore()
        try {
            val entered = CompletableDeferred<Transaction>()
            val key = testKey(store)
            val operation = launch(Dispatchers.Default) {
                store.tc.runSuspending(FoundationDBTransactionContext(FoundationDBTransactionKind.Request)) { transaction ->
                    transaction.set(key, byteArrayOf(1))
                    entered.complete(transaction)
                    awaitCancellation()
                }
            }
            val transaction = withTimeout(5.seconds) { entered.await() }
            withTimeout(5.seconds) { operation.cancelAndJoin() }
            assertClosed(transaction)
            assertNull(store.tc.run { it.get(key).awaitResult() })
        } finally { store.close() }
    }

    private suspend fun assertClosed(transaction: Transaction) = withTimeout(5.seconds) {
        while (true) {
            try {
                // A cancelled-but-open handle still returns a failed future. Closing the
                // actual handle makes this call fail synchronously on JVM and Native.
                transaction.getReadVersion().cancel()
            } catch (_: IllegalStateException) {
                return@withTimeout
            }
            // Native coroutine cancellation may finish its ownership cleanup asynchronously.
            delay(10.milliseconds)
        }
    }

    private fun testKey(store: FoundationDBDataStore) =
        packKey(store.getTableDirs(1u).modelPrefix, "tracked-cancellation".encodeToByteArray())

    private suspend fun openStore(path: List<String> = listOf("maryk", "tracked-cancellation", Uuid.random().toString())) =
        FoundationDBDataStore.open(directoryPath = path, dataModelsById = mapOf(1u to SimpleMarykModel), keepAllVersions = false)
}
