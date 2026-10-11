package maryk.datastore.indexeddb

import kotlinx.coroutines.test.runTest
import maryk.datastore.shared.AtomicAbort
import maryk.datastore.shared.MAX_ATOMIC_WORK_BYTES
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AtomicStagingTest {
    @Test
    fun smallScanDoesNotLoadOrRejectALargeRange() = runTest {
        val native = RowsByteStore(100_001)
        val staged = StagedIndexedDbByteStore(native)
        assertEquals(listOf(1), staged.scan("rows", limit = 1u).map { number(it.first) })
        assertEquals(1, native.fetched)
        assertEquals(listOf(100_001), staged.scan("rows", reverse = true, limit = 1u).map { number(it.first) })
        assertEquals(2, native.fetched)
        val bounded = RowsByteStore(100_001)
        assertEquals(100_000, StagedIndexedDbByteStore(bounded).scan("rows", limit = 100_000u).size)
        assertEquals(100_000, bounded.fetched)
    }

    @Test
    fun mergesAcrossPagesWithDeletesOverwritesReverseAndBounds() = runTest {
        val native = RowsByteStore(2000)
        val staged = StagedIndexedDbByteStore(native)
        for (i in 1..300) staged.delete("rows", key(i))
        staged.put("rows", key(0), byteArrayOf(5))
        staged.put("rows", key(2), byteArrayOf(6))
        staged.put("rows", key(2), byteArrayOf(7))
        staged.put("other", key(1), byteArrayOf(9))
        val forward = staged.scan("rows", limit = 10u)
        assertEquals(listOf(0, 2) + (301..308), forward.map { number(it.first) })
        assertEquals(7, staged.get("rows", key(2))!!.single().toInt())
        assertEquals(302, staged.operations.size)
        assertTrue(native.fetched <= 310)
        assertEquals(listOf(309, 308, 307, 306, 305), staged.scan("rows", key(304), false, key(310), false, true, 5u).map { number(it.first) })
        assertTrue(staged.scan("rows", limit = 0u).isEmpty())
    }

    @Test
    fun workAndScanBudgetsHaveStructuredRejections() = runTest {
        val staged = StagedIndexedDbByteStore(RowsByteStore(0))
        val bytes = assertFailsWith<AtomicAbort> { staged.put("rows", key(1), ByteArray(MAX_ATOMIC_WORK_BYTES)) }
        assertEquals("workBytes", bytes.response.failure?.limit)
        assertEquals(MAX_ATOMIC_WORK_BYTES.toULong(), bytes.response.failure?.maximum)
        assertEquals((MAX_ATOMIC_WORK_BYTES + 4).toULong(), bytes.response.failure?.actual)
        val rows = assertFailsWith<AtomicAbort> { StagedIndexedDbByteStore(RowsByteStore(100_001)).scan("rows") }
        assertEquals("scanRows", rows.response.failure?.limit)
        assertEquals(100_001uL, rows.response.failure?.actual)
    }

    private class RowsByteStore(private val rows: Int) : IndexedDbByteStore {
        var fetched = 0
        override suspend fun close() {}
        override suspend fun get(storeName: String, key: ByteArray): ByteArray? = byteArrayOf(1)
        override suspend fun put(storeName: String, key: ByteArray, value: ByteArray) = error("Unexpected native write")
        override suspend fun delete(storeName: String, key: ByteArray) = error("Unexpected native delete")
        override suspend fun scan(storeName: String, startKey: ByteArray?, includeStart: Boolean, endKey: ByteArray?, includeEnd: Boolean, reverse: Boolean, limit: UInt): List<Pair<ByteArray, ByteArray>> {
            check(limit <= 256u)
            val first = maxOf(1, startKey?.let { number(it) + if (includeStart) 0 else 1 } ?: 1)
            val last = minOf(rows, endKey?.let { number(it) - if (includeEnd) 0 else 1 } ?: rows)
            val range = if (reverse) last downTo first else first..last
            val result = range.asSequence().take(limit.toInt()).map { key(it) to byteArrayOf(1) }.toList()
            fetched += result.size
            return result
        }
    }
    companion object {
        private fun key(value: Int) = ByteArray(4) { i -> (value ushr (24 - i * 8)).toByte() }
        private fun number(key: ByteArray) = key.fold(0) { value, byte -> (value shl 8) or (byte.toInt() and 255) }
    }
}
