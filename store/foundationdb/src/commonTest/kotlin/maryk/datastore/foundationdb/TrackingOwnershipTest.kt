package maryk.datastore.foundationdb

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.test.dataModelsForTests
import maryk.foundationdb.FdbFuture
import maryk.foundationdb.Transaction
import maryk.foundationdb.TransactionContext
import maryk.foundationdb.completedFdbFuture
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

class TrackingOwnershipTest {
    @Test
    fun completingOneFutureDoesNotLoseTheOtherTransactionsCancellation() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "tracking-ownership", Uuid.random().toString()),
            dataModelsById = dataModelsForTests,
        )
        val returned = List(2) { CompletableDeferred<Unit>() }
        val release = List(2) { CompletableDeferred<Unit>() }
        val next = atomic(0)
        val barrier = object : TransactionContext by store.tc {
            override fun <T> runAsync(block: (Transaction) -> FdbFuture<T>): FdbFuture<T> = store.tc.runAsync { transaction ->
                val slot = next.getAndIncrement()
                val result = block(transaction)
                foundationDbFuture {
                    val value = result.await()
                    returned[slot].complete(Unit)
                    release[slot].await()
                    value
                }
            }
        }
        val tracked = TrackingTransactionContext(barrier)
        val prefix = store.getTableDirs(1u).modelPrefix
        val firstKey = packKey(prefix, byteArrayOf(123))
        val secondKey = packKey(prefix, byteArrayOf(124))
        try {
            val first = tracked.runAsync { transaction ->
                transaction.set(firstKey, byteArrayOf(1))
                completedFdbFuture(Unit)
            }
            returned[0].await()
            val second = tracked.runAsync { transaction ->
                transaction.set(secondKey, byteArrayOf(2))
                completedFdbFuture(Unit)
            }
            returned[1].await()
            release[0].complete(Unit)
            first.await()
            tracked.cancelActive()
            release[1].complete(Unit)
            assertTrue(runCatching { second.await() }.isFailure)
            assertContentEquals(byteArrayOf(1), store.tc.run { it.get(firstKey).awaitResult() })
            assertNull(store.tc.run { it.get(secondKey).awaitResult() })
        } finally {
            release.forEach { it.complete(Unit) }
            tracked.cancelActive()
            store.close()
        }
    }
}
