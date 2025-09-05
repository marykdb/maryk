package maryk.datastore.foundationdb

import maryk.datastore.foundationdb.processors.helpers.decodeZeroFreeUsing01
import maryk.datastore.foundationdb.processors.helpers.encodeZeroFreeUsing01
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ZeroFreeEncodingTest {
    @Test
    fun roundTripSimpleCases() {
        val cases = listOf(
            byteArrayOf(),
            byteArrayOf(0x00),
            byteArrayOf(0x01),
            byteArrayOf(0x02),
            byteArrayOf(0x7F),
            byteArrayOf(0x00, 0x01, 0x02, 0x03),
            byteArrayOf(0x01, 0x01, 0x00, 0x05, 0x01),
            byteArrayOf(-1), // 0xFF
            byteArrayOf(0x00, -1, 0x01, 0x00, 0x10)
        )

        for (src in cases) {
            val enc = encodeZeroFreeUsing01(src)
            // Encoded should not contain 0x00
            assertTrue(enc.none { it.toInt() == 0x00 })
            val dec = decodeZeroFreeUsing01(enc)
            assertContentEquals(src, dec)
        }
    }

    @Test
    fun roundTripRandom() {
        val rnd = Random(12345)
        repeat(50) { _ ->
            val len = rnd.nextInt(0, 128)
            val src = ByteArray(len) { rnd.nextInt(0, 256).toByte() }
            val enc = encodeZeroFreeUsing01(src)
            assertTrue(enc.none { it.toInt() == 0x00 })
            val dec = decodeZeroFreeUsing01(enc)
            assertContentEquals(src, dec)
        }
    }

    @Test
    fun invalidEscapesThrow() {
        // Truncated escape at end
        assertFailsWith<IllegalArgumentException> {
            decodeZeroFreeUsing01(byteArrayOf(0x01))
        }

        // Invalid second byte after 0x01 (e.g., 0x00)
        assertFailsWith<IllegalStateException> {
            decodeZeroFreeUsing01(byteArrayOf(0x01, 0x00))
        }

        // Encoded stream should not contain raw 0x00
        assertFailsWith<IllegalArgumentException> {
            decodeZeroFreeUsing01(byteArrayOf(0x00))
        }
    }
}

