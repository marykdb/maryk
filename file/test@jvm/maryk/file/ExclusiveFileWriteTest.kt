package maryk.file

import java.nio.ByteBuffer
import java.nio.channels.WritableByteChannel
import kotlin.test.Test
import kotlin.test.assertEquals

class ExclusiveFileWriteTest {
    @Test
    fun writeFullyRetriesShortChannelWrites() {
        val output = mutableListOf<Byte>()
        val channel = object : WritableByteChannel {
            override fun isOpen() = true
            override fun close() = Unit
            override fun write(source: ByteBuffer): Int {
                output += source.get()
                return 1
            }
        }

        writeFully(channel, byteArrayOf(1, 2, 3))

        assertEquals(listOf<Byte>(1, 2, 3), output)
    }
}
