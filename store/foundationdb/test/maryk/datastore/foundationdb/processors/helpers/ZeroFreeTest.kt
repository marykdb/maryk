package maryk.datastore.foundationdb.processors.helpers

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals

class ZeroFreeTest {
    @Test
    fun encodedLengthMatchesEscapedOutput() {
        val source = byteArrayOf(0x00, 0x01, 0x02, 0x7f)

        assertEquals(6, source.zeroFreeEncodedLength())
        assertContentEquals(
            byteArrayOf(0x01, 0x01, 0x01, 0x02, 0x02, 0x7f),
            encodeZeroFreeUsing01(source)
        )
    }

    @Test
    fun checkedZeroFreeLengthRejectsOverflow() {
        assertFailsWith<IllegalArgumentException> {
            Int.MAX_VALUE.checkedZeroFreeLengthPlus(1)
        }
    }

    @Test
    fun checkedZeroFreeLengthRejectsNegativeAddend() {
        assertFailsWith<IllegalArgumentException> {
            0.checkedZeroFreeLengthPlus(-1)
        }
    }
}
