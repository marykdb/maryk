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
    fun compareTo() {
        expect(0) { byteArrayOf(1, 2, 3).compareTo(byteArrayOf(1, 2, 3)) }
        assertTrue { byteArrayOf(1, 2, 3).compareTo(byteArrayOf(1, 2, 4)) < 0 }
        assertTrue { byteArrayOf(1, 2, 4).compareTo(byteArrayOf(1, 2, 3)) > 0 }
        assertTrue { byteArrayOf(1, 2).compareTo(byteArrayOf(1, 2, 0)) < 0 }
    }

    @Test
    fun compareToWithOffsetLength() {
        expect(0) { byteArrayOf(2, 3).compareToWithOffsetLength(byteArrayOf(1, 2, 3, 4), 1, 2) }
        assertTrue { byteArrayOf(2, 4).compareToWithOffsetLength(byteArrayOf(1, 2, 3, 4), 1, 2) > 0 }
        assertTrue { byteArrayOf(2).compareToWithOffsetLength(byteArrayOf(1, 2, 3, 4), 1, 2) < 0 }
    }

    @Test
    fun compareDefinedTo() {
        expect(0) { byteArrayOf(1, 2, 3).compareDefinedTo(byteArrayOf(1, 2, 3, 4), 0, 3) }
        assertTrue { byteArrayOf(1, 2, 3, 4).compareDefinedTo(byteArrayOf(1, 2, 3), 0, 3) > 0 }
        assertTrue { byteArrayOf(1, 2, 4).compareDefinedTo(byteArrayOf(1, 2, 3), 0, 3) > 0 }
        assertTrue { byteArrayOf(1, 2, 3, 4).compareDefinedTo(byteArrayOf(1, 2, 3), 0, 2) > 0 }
    }

    @Test
    fun match() {
        assertTrue { byteArrayOf(1, 2, 3).match(0, byteArrayOf(1, 2, 3)) }
        assertTrue { byteArrayOf(0, 1, 2, 3, 0).match(1, byteArrayOf(1, 2, 3), 3) }
        assertFalse { byteArrayOf(1, 2, 3).match(0, byteArrayOf(1, 2), 3) }
        assertFalse { byteArrayOf(1, 2, 3).match(0, byteArrayOf(1, 3, 3), 3) }
    }

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
        expect("0100ff") { initByteArrayByHex("010100").prevByteInSameLength(2).toHex() }
    }
}
