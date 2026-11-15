package maryk.datastore.foundationdb

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import maryk.foundationdb.tuple.Tuple
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class OrdinaryReceiptSessionTest {
    @Test
    fun allocatesOneOwnerLazilyAndProducesUniqueCanonicalKeysAcrossConcurrentCalls() = runTest {
        val allocations = atomic(0)
        val session = OrdinaryReceiptSession { delay(10); allocations.incrementAndGet().toLong() }
        val prefix = byteArrayOf(0, 1, -1)
        assertNull(session.range(prefix))
        val keys = List(128) { async(Dispatchers.Default) { session.nextKey(prefix) } }.awaitAll()
        assertEquals(1, allocations.value)
        assertEquals(keys.size, keys.map { it.toHexString() }.toSet().size)
        val range = assertNotNull(session.range(prefix))
        val canonical = prefix + Tuple.from(Uuid.fromLongs(0L, 1L).toString()).pack()
        assertContentEquals(canonical, range.begin)
        assertEquals(
            (1L..128L).map { packReceiptKey(prefix, canonical.copyOfRange(prefix.size, canonical.size), Uuid.fromLongs(0L, it)).toHexString() }.toSet(),
            keys.map { it.toHexString() }.toSet(),
        )
        val second = OrdinaryReceiptSession { allocations.incrementAndGet().toLong() }
        val other = second.nextKey(prefix)
        assertTrue(keys.none { it.contentEquals(other) })
        assertTrue(other.toHexString() >= range.end.toHexString())
    }

    @Test
    fun failedAllocationDoesNotPublishAnOwnerOrReuseAnOperationKey() = runTest {
        var calls = 0
        val session = OrdinaryReceiptSession { if (++calls == 1) error("injected allocation failure"); 17L }
        val prefix = byteArrayOf(9)
        assertFailsWith<IllegalStateException> { session.nextKey(prefix) }
        assertNull(session.range(prefix))
        val first = session.nextKey(prefix)
        val second = session.nextKey(prefix)
        assertEquals(2, calls)
        assertTrue(!first.contentEquals(second))
        assertContentEquals(packReceiptKey(prefix, Tuple.from(Uuid.fromLongs(0L, 17L).toString()).pack(), Uuid.fromLongs(0L, 1L)), first)
    }

    @Test
    fun ownerCounterRejectsCorruptionAndExhaustionRatherThanReusingAName() {
        val first = advanceOrdinaryReceiptSessionCounter(null)
        assertEquals(1L, first.first)
        assertContentEquals(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 1), first.second)
        assertEquals(2L, advanceOrdinaryReceiptSessionCounter(first.second).first)
        for (bad in listOf(byteArrayOf(1), ByteArray(8), ByteArray(8) { -1 }, byteArrayOf(127, -1, -1, -1, -1, -1, -1, -1))) {
            assertFailsWith<IllegalStateException> { advanceOrdinaryReceiptSessionCounter(bad) }
        }
    }
}
