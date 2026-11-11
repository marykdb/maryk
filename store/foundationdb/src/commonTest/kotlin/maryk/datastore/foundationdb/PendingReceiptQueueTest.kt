package maryk.datastore.foundationdb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import maryk.core.properties.types.Bytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PendingReceiptQueueTest {
    @Test fun capacityAndInputOwnershipArePreservedAcrossDrain() {
        val queue = PendingReceiptQueue(2)
        val key = byteArrayOf(1)
        assertTrue(queue.offer(key)); key[0] = 9
        assertTrue(queue.offer(byteArrayOf(2)))
        assertFalse(queue.offer(byteArrayOf(3)))
        assertEquals(setOf(Bytes(byteArrayOf(1)),Bytes(byteArrayOf(2))),queue.drain().toSet())
        assertTrue(queue.drain().isEmpty())
        assertTrue(queue.offer(byteArrayOf(3)))
    }

    @Test fun failureRestorePrioritizesOlderKeysAndDeduplicatesReplay() {
        val queue = PendingReceiptQueue(3)
        queue.offer(byteArrayOf(1)); queue.offer(byteArrayOf(2))
        val older = queue.drain()
        queue.offer(byteArrayOf(2)); queue.offer(byteArrayOf(3)); queue.offer(byteArrayOf(4))
        queue.restore(older)
        val restored = queue.drain()
        assertEquals(3,restored.size)
        assertTrue(restored.containsAll(older))
        assertEquals(3,restored.toSet().size)
    }

    @Test fun concurrentOffersDoNotLoseKeys() = runBlocking {
        val queue = PendingReceiptQueue(10_000)
        List(8) { worker -> async(Dispatchers.Default) {
            repeat(1000) { index ->
                val value = worker*1000+index
                assertTrue(queue.offer(byteArrayOf((value ushr 8).toByte(),value.toByte())))
            }
        } }.awaitAll()
        val keys = queue.drain()
        assertEquals(8000,keys.size)
        assertEquals(8000,keys.toSet().size)
    }
}
