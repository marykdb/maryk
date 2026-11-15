package maryk.datastore.remote

import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

class ExecuteFrameReadTest {
    @Test
    fun largeFramesKeepTheirBoundariesAndOwnTheirPayloads() = runTest {
        val first = ByteArray(20_003) { it.toByte() }
        val second = byteArrayOf(10, 20)
        val wire = RemoteStoreCodec.lengthPrefix(first.size) + first +
            RemoteStoreCodec.lengthPrefix(second.size) + second
        val decoded = readExecuteFrames(ByteReadChannel(wire), 2) { bytes, _ -> bytes }
        assertContentEquals(first, decoded[0])
        assertContentEquals(second, decoded[1])
        wire.fill(0)
        assertContentEquals(first, decoded[0])
        assertContentEquals(second, decoded[1])
        assertFailsWith<IllegalStateException> {
            val truncated = RemoteStoreCodec.lengthPrefix(first.size) + first.copyOf(first.size - 1)
            readExecuteFrames(ByteReadChannel(truncated), 1) { _, _ -> Unit }
        }
    }

    @Test
    fun decodesEachFrameBeforeReadingTheNextOne() = runTest(timeout = 5.seconds) {
        val channel = ByteChannel(autoFlush = true)
        val firstDecoded = CompletableDeferred<Unit>()
        val writer = launch {
            for (byte in RemoteStoreCodec.lengthPrefix(1)) channel.writeFully(byteArrayOf(byte))
            channel.writeFully(byteArrayOf(10))
            firstDecoded.await()
            channel.writeFully(RemoteStoreCodec.lengthPrefix(1))
            channel.writeFully(byteArrayOf(20))
            channel.close()
        }
        val decoded = readExecuteFrames(channel, 2) { bytes, index ->
            if (index == 0) firstDecoded.complete(Unit)
            bytes.single().toInt()
        }
        writer.join()
        assertEquals(listOf(10, 20), decoded)
    }

    @Test
    fun rejectsInvalidTruncatedOversizedAndTrailingFrames() = runTest {
        val frame = RemoteStoreCodec.lengthPrefix(1) + byteArrayOf(10)
        val invalidBodies = listOf(
            byteArrayOf(-1, -1, -1, -1),
            RemoteStoreCodec.lengthPrefix(0),
            RemoteStoreCodec.lengthPrefix(16 * 1024 * 1024 + 1),
            byteArrayOf(0, 0),
            RemoteStoreCodec.lengthPrefix(5) + byteArrayOf(10),
            frame,
            frame + frame + byteArrayOf(1),
        )
        for (bytes in invalidBodies) {
            assertFailsWith<IllegalStateException> {
                readExecuteFrames(ByteReadChannel(bytes), 2) { payload, _ -> payload.single() }
            }
        }
        assertFailsWith<IllegalStateException> {
            readExecuteFrames(ByteReadChannel(frame + frame), 2, maxBytes = 9) { payload, _ -> payload.single() }
        }
        assertFailsWith<CancellationException> {
            readExecuteFrames(ByteReadChannel(frame + frame), 2) { _, _ -> throw CancellationException("cancelled") }
        }
    }
}
