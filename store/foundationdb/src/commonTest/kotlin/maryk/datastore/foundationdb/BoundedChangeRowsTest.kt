package maryk.datastore.foundationdb

import kotlinx.coroutines.runBlocking
import maryk.datastore.foundationdb.processors.helpers.collectBoundedRows
import maryk.foundationdb.FdbFuture
import maryk.foundationdb.KeyValue
import maryk.foundationdb.async.AsyncIterable
import maryk.foundationdb.async.AsyncIterator
import maryk.foundationdb.completedFdbFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class BoundedChangeRowsTest {
    @Test fun completesAndClosesAtExactLimit() = runBlocking {
        val source = Rows()
        assertEquals(source.rows, source.collectBoundedRows(FoundationDBReadBudget(6)))
        assertEquals(3, source.consumed)
        assertEquals(1, source.closed)
    }

    @Test fun exceededRecordLimitStopsAndClosesCursor() = runBlocking {
        val source = Rows()
        val failure = assertFailsWith<FoundationDBReadLimitExceededException> {
            source.collectBoundedRows(FoundationDBReadBudget(3))
        }
        assertEquals(3L, failure.limitBytes)
        assertEquals(2, source.consumed)
        assertEquals(1, source.closed)
    }

    @Test fun requestBudgetIncludesEarlierRecords() = runBlocking {
        val request = FoundationDBReadBudget(8)
        Rows().collectBoundedRows(FoundationDBReadBudget(6, request))
        val source = Rows()
        val failure = assertFailsWith<FoundationDBReadLimitExceededException> {
            source.collectBoundedRows(FoundationDBReadBudget(6, request))
        }
        assertEquals(8L, failure.limitBytes)
        assertEquals(2, source.consumed)
        assertEquals(1, source.closed)
    }

    @Test fun readFailureClosesCursorAndPreservesFailure() = runBlocking {
        val failure = IllegalStateException("read failed")
        val source = Rows(failure)
        assertSame(failure, assertFailsWith<IllegalStateException> {
            source.collectBoundedRows(FoundationDBReadBudget(6))
        })
        assertEquals(1, source.closed)
    }

    private class Rows(val failure: Throwable? = null) : AsyncIterable<KeyValue> {
        val rows = List(3) { KeyValue(byteArrayOf(it.toByte()), byteArrayOf(1)) }
        var consumed = 0
        var closed = 0
        override fun asList(): FdbFuture<List<KeyValue>> = error("Unbounded materialization")
        override fun iterator(): AsyncIterator<KeyValue> = object : AsyncIterator<KeyValue> {
            override fun hasNext() = consumed < rows.size
            override fun onHasNext() = completedFdbFuture(hasNext())
            override suspend fun next(): KeyValue {
                failure?.let { throw it }
                return rows[consumed++]
            }
            override fun cancel() { closed++ }
        }
    }
}
