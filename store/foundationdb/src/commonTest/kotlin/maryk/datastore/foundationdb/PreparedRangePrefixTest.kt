package maryk.datastore.foundationdb

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import maryk.datastore.foundationdb.processors.helpers.PreparedRangeRead
import maryk.foundationdb.FdbFuture
import maryk.foundationdb.FutureCancellation
import maryk.foundationdb.KeyValue
import maryk.foundationdb.asFdbFuture
import maryk.foundationdb.async.CloseableAsyncIterator
import maryk.foundationdb.completedFdbFuture
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PreparedRangePrefixTest {
    private val marker = KeyValue(byteArrayOf(1), byteArrayOf(2, 3))
    private val deleted = KeyValue(byteArrayOf(1, 0), byteArrayOf(4, 5))
    private val first = KeyValue(byteArrayOf(1, 8), byteArrayOf(6, 7))
    private val last = KeyValue(byteArrayOf(1, 16), byteArrayOf(8, 9))

    private class Rows(
        private val input: List<KeyValue>,
        private val pending: FdbFuture<Boolean>? = null,
        private val failureAfterFirst: Throwable? = null,
        private val closeFailure: Throwable? = null,
    ) : CloseableAsyncIterator<KeyValue> {
        var index = 0
        var closes = 0
        override fun hasNext() = index < input.size
        override suspend fun next() = input[index++]
        override fun onHasNext(): FdbFuture<Boolean> {
            if (index == 1 && failureAfterFirst != null) throw failureAfterFirst
            return if (index >= input.size && pending != null) pending else completedFdbFuture(hasNext())
        }
        override fun cancel() = close()
        override fun close() { closes++; pending?.cancel(); closeFailure?.let { throw it } }
    }

    @Test fun absentMarkerRetainsTheFirstFollowingRowAndChargesItOnce() = runBlocking<Unit> {
        val expected = listOf(marker, first, last)
        val iterator = Rows(expected)
        val bytes = expected.sumOf { it.key.size.toLong() + it.value.size }
        val parent = FoundationDBReadBudget(bytes)
        val read = PreparedRangeRead(iterator, FoundationDBReadBudget(bytes, parent))
        assertNull(read.awaitPrefixValue(deleted.key))
        assertEquals(2, iterator.index)
        assertNull(read.awaitPrefixValue(deleted.key))
        assertEquals(expected, read.awaitRows())
        assertEquals(1, iterator.closes)
        assertFailsWith<FoundationDBReadLimitExceededException> { parent.consumeMemoizedBytes(1) }
        read.cancel()
        assertEquals(1, iterator.closes)
    }

    @Test fun presentMarkerCanFilterWithoutConsumingOrChargingTheRecordBody() = runBlocking<Unit> {
        val iterator = Rows(listOf(marker, deleted, first, last))
        val parent = FoundationDBReadBudget(1)
        val read = PreparedRangeRead(iterator, FoundationDBReadBudget(1, parent))
        assertContentEquals(deleted.value, read.awaitPrefixValue(deleted.key))
        assertEquals(2, iterator.index)
        parent.consumeMemoizedBytes(1)
        read.cancel()
        assertEquals(1, iterator.closes)
        assertFailsWith<IllegalStateException> { read.awaitRows() }
    }

    @Test fun materializationEnforcesRecordAndSharedLimitsAfterPeeking() = runBlocking<Unit> {
        for (shared in listOf(false, true)) {
            val iterator = Rows(listOf(marker, deleted, first, last))
            val read = PreparedRangeRead(iterator, FoundationDBReadBudget(if (shared) 100 else 8,
                if (shared) FoundationDBReadBudget(8) else null))
            assertContentEquals(deleted.value, read.awaitPrefixValue(deleted.key))
            assertFailsWith<FoundationDBReadLimitExceededException> { read.awaitRows() }
            assertEquals(1, iterator.closes)
        }
    }

    @Test fun emptyRangeAndMarkerOnlyRangeMaterializeWithoutLosingRows() = runBlocking<Unit> {
        for (expected in listOf(emptyList(), listOf(marker))) {
            val iterator = Rows(expected)
            val read = PreparedRangeRead(iterator, FoundationDBReadBudget(100))
            assertNull(read.awaitPrefixValue(deleted.key))
            assertEquals(expected, read.awaitRows())
            assertEquals(1, iterator.closes)
        }
    }

    @Test fun cancellationWhilePeekingClosesIteratorAndCancelsItsPendingFuture() = runBlocking<Unit> {
        val pending = CompletableDeferred<Boolean>()
        val iterator = Rows(listOf(marker), pending.asFdbFuture(FutureCancellation.PROPAGATE))
        val read = PreparedRangeRead(iterator, FoundationDBReadBudget(100))
        val job = launch { read.awaitPrefixValue(deleted.key) }
        yield()
        job.cancelAndJoin()
        assertTrue(pending.isCancelled)
        assertEquals(1, iterator.closes)
        read.cancel()
        assertEquals(1, iterator.closes)
    }

    @Test fun peekFailureKeepsItsCauseWhenIteratorCleanupAlsoFails() = runBlocking<Unit> {
        val failure = IllegalStateException("native range failed")
        val cleanup = IllegalStateException("close failed")
        val iterator = Rows(listOf(marker), failureAfterFirst = failure, closeFailure = cleanup)
        val read = PreparedRangeRead(iterator, FoundationDBReadBudget(100))
        assertSame(failure, assertFailsWith<IllegalStateException> { read.awaitPrefixValue(deleted.key) })
        assertSame(cleanup, failure.suppressedExceptions.single())
        assertEquals(1, iterator.closes)
    }
}
