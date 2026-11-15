package maryk.datastore.remote

import com.sun.management.ThreadMXBean
import io.ktor.utils.io.ByteReadChannel
import java.lang.management.ManagementFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RemoteReadAllocationTest {
    @Test
    fun largeContentLengthsDoNotAllocateUnreadBodies() = runTest {
        val allocations = allocationCounter() ?: return@runTest
        val payload = byteArrayOf(1, 2, 3)
        repeat(4) { ByteReadChannel(payload).readBoundedBytes(1025, expectedSize = 1024) }
        val before = allocations.currentThreadBytes()
        for (declared in listOf(16 * 1024 * 1024, 64 * 1024 * 1024)) {
            assertContentEquals(payload, ByteReadChannel(payload).readBoundedBytes(declared + 1, expectedSize = declared))
        }
        val allocated = allocations.currentThreadBytes() - before
        assertTrue(allocated < 1024 * 1024, "Truncated bodies allocated $allocated bytes from their declared lengths")
    }

    @Test
    fun largeFramePrefixesDoNotAllocateUnreadPayloads() = runTest {
        val allocations = allocationCounter() ?: return@runTest
        repeat(4) {
            assertFailsWith<IllegalStateException> {
                readExecuteFrames(ByteReadChannel(RemoteStoreCodec.lengthPrefix(1)), 1) { _, _ -> Unit }
            }
        }
        val before = allocations.currentThreadBytes()
        assertFailsWith<IllegalStateException> {
            readExecuteFrames(ByteReadChannel(RemoteStoreCodec.lengthPrefix(16 * 1024 * 1024)), 1) { _, _ -> Unit }
        }
        val allocated = allocations.currentThreadBytes() - before
        assertTrue(allocated < 1024 * 1024, "Truncated frame allocated $allocated bytes from its length prefix")
    }

    private fun allocationCounter(): ThreadMXBean? {
        val bean = ManagementFactory.getThreadMXBean() as? ThreadMXBean ?: return null
        if (!bean.isThreadAllocatedMemorySupported) return null
        if (!bean.isThreadAllocatedMemoryEnabled) bean.isThreadAllocatedMemoryEnabled = true
        return bean
    }

    private fun ThreadMXBean.currentThreadBytes(): Long = getThreadAllocatedBytes(Thread.currentThread().threadId())
}
