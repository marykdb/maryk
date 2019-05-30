package maryk.lib.extensions.compare

import maryk.lib.extensions.initByteArrayByHex
import maryk.lib.extensions.toHex
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.expect

class ByteArrayKtTest {

    @Test
    fun matchPart() {
        assertTrue { byteArrayOf(1, 2, 3).matchPart(0, byteArrayOf(1, 2)) }
        assertTrue { byteArrayOf(1, 2, 3).matchPart(1, byteArrayOf(2, 3)) }
        assertFalse { byteArrayOf(1, 2, 3).matchPart(1, byteArrayOf(3, 3)) }
    }

    @Test
    fun nextByteInSameLength() {
        expect("000001") { initByteArrayByHex("000000").nextByteInSameLength().toHex() }
        expect("0001ff") { initByteArrayByHex("0000ff").nextByteInSameLength().toHex() }
        expect("01ffff") { initByteArrayByHex("00ffff").nextByteInSameLength().toHex() }
        expect("ffffff") { initByteArrayByHex("ffffff").nextByteInSameLength().toHex() }
        expect("0000ff") { initByteArrayByHex("0000fe").nextByteInSameLength().toHex() }
    }

    @Test
    fun prevByteInSameLength() {
        assertFailsWith<IllegalStateException> {
            initByteArrayByHex("000000").prevByteInSameLength()
        }

        expect("000000") { initByteArrayByHex("000001").prevByteInSameLength().toHex() }
        expect("0000fe") { initByteArrayByHex("0000ff").prevByteInSameLength().toHex() }
        expect("0000ff") { initByteArrayByHex("000100").prevByteInSameLength().toHex() }
        expect("00ffff") { initByteArrayByHex("010000").prevByteInSameLength().toHex() }
        expect("fffffe") { initByteArrayByHex("ffffff").prevByteInSameLength().toHex() }
    }
}
