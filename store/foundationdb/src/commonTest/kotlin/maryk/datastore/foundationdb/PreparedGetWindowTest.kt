package maryk.datastore.foundationdb

import maryk.core.models.RootDataModel
import maryk.core.properties.types.Key
import maryk.datastore.foundationdb.processors.helpers.PreparedGetRecord
import maryk.datastore.foundationdb.processors.helpers.PreparedGetWindow
import maryk.datastore.foundationdb.processors.helpers.PreparedRecordRead
import maryk.foundationdb.FdbFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

private object WindowRecord : RootDataModel<WindowRecord>()

class PreparedGetWindowTest {
    private class Future(private val failure: Throwable? = null) : FdbFuture<ByteArray?> {
        var cancellations = 0
        override suspend fun await(): ByteArray? = null
        override suspend fun awaitObserved(): ByteArray? = null
        override val isDone get() = false
        override val isCancelled get() = cancellations > 0
        override fun cancel() { cancellations++; failure?.let { throw it } }
    }

    private fun key(value: Byte) = Key<WindowRecord>(ByteArray(16) { value })

    @Test
    fun transferMovesAllWorkAndLeavesUnconsumedRecordsWithTheWindow() {
        val first = key(1)
        val second = key(2)
        val creations = listOf(Future(), Future())
        val rows = listOf(Future(), Future())
        val deletions = listOf(Future(), Future())
        val window = PreparedGetWindow.prepare(listOf(first, second)) { key ->
            val index = if (key == first) 0 else 1
            PreparedGetRecord().also {
                it.creation = creations[index]
                it.read = PreparedRecordRead(listOf(byteArrayOf(1) to rows[index]), emptyList(), FoundationDBReadBudget(100))
                it.softDelete = deletions[index]
            }
        }
        val record = window.take(first)!!
        assertNull(window.take(first))
        window.release()
        window.release()
        assertEquals(listOf(0, 1), creations.map { it.cancellations })
        assertEquals(listOf(0, 1), rows.map { it.cancellations })
        assertEquals(listOf(0, 1), deletions.map { it.cancellations })
        record.release()
        record.release()
        assertEquals(listOf(1, 1), creations.map { it.cancellations })
        assertEquals(listOf(1, 1), rows.map { it.cancellations })
        assertEquals(listOf(1, 1), deletions.map { it.cancellations })
        assertFailsWith<IllegalStateException> { window.take(second) }
    }

    @Test
    fun failedPreparationReleasesAlreadyTransferredWorkAndPreservesItsCause() {
        val first = key(1)
        val second = key(2)
        val cleanup = IllegalStateException("cancel failed")
        val creation = Future(cleanup)
        val early = PreparedGetWindow.prepare(listOf(first)) { PreparedGetRecord().also { it.creation = creation } }
        val failure = IllegalStateException("second preparation failed")
        val actual = assertFailsWith<IllegalStateException> {
            PreparedGetWindow.prepare(listOf(first, second)) { key ->
                if (key == first) early.take(key)!! else throw failure
            }
        }
        assertSame(failure, actual)
        assertSame(cleanup, actual.suppressedExceptions.single())
        early.release()
        assertEquals(1, creation.cancellations)
    }

    @Test
    fun recordCleanupAttemptsEveryResourceEvenWhenEarlierCancellationFails() {
        val failure = IllegalStateException("creation cancel failed")
        val rowFailure = IllegalStateException("row cancel failed")
        val creation = Future(failure)
        val row = Future(rowFailure)
        val deletion = Future()
        val record = PreparedGetRecord().also {
            it.creation = creation
            it.read = PreparedRecordRead(listOf(byteArrayOf(1) to row), emptyList(), FoundationDBReadBudget(100))
            it.softDelete = deletion
        }
        assertSame(failure, assertFailsWith<IllegalStateException> { record.release() })
        assertSame(rowFailure, failure.suppressedExceptions.single())
        record.release()
        assertEquals(1, creation.cancellations)
        assertEquals(1, row.cancellations)
        assertEquals(1, deletion.cancellations)
    }

    @Test
    fun windowCleanupContinuesPastAFailedRecordAndRejectsReuse() {
        val failure = IllegalStateException("first cancel failed")
        val creations = listOf(Future(failure), Future())
        var index = 0
        val window = PreparedGetWindow.prepare(listOf(key(1), key(2))) {
            PreparedGetRecord().also { it.creation = creations[index++] }
        }
        assertSame(failure, assertFailsWith<IllegalStateException> { window.release() })
        window.release()
        assertEquals(listOf(1, 1), creations.map { it.cancellations })
        assertFailsWith<IllegalStateException> { window.peek(key(1)) }
    }
}
