package maryk.lib.extensions.compare

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
    fun compareToRange() {
        expect(0) { byteArrayOf(2, 3).compareToRange(byteArrayOf(1, 2, 3, 4), 1, 2) }
        assertTrue { byteArrayOf(2, 4).compareToRange(byteArrayOf(1, 2, 3, 4), 1, 2) > 0 }
        assertTrue { byteArrayOf(2).compareToRange(byteArrayOf(1, 2, 3, 4), 1, 2) < 0 }
        assertFailsWith<IndexOutOfBoundsException> {
            byteArrayOf(1).compareToRange(byteArrayOf(1, 2), 2, 1)
        }
    }

    @Test
    fun compareDefinedRange() {
        expect(0) { byteArrayOf(1, 2, 3).compareDefinedRange(byteArrayOf(1, 2, 3, 4), 0, 3) }
        assertTrue { byteArrayOf(1, 2, 3, 4).compareDefinedRange(byteArrayOf(1, 2, 3), 0, 3) > 0 }
        assertTrue { byteArrayOf(1, 2, 4).compareDefinedRange(byteArrayOf(1, 2, 3), 0, 3) > 0 }
        assertTrue { byteArrayOf(1, 2, 3, 4).compareDefinedRange(byteArrayOf(1, 2, 3), 0, 2) > 0 }
        assertFailsWith<IndexOutOfBoundsException> {
            byteArrayOf(1, 2).compareDefinedRange(byteArrayOf(1), 1, 1)
        }
    }

    @Test
    fun match() {
        assertTrue { byteArrayOf(1, 2, 3).matchesRange(0, byteArrayOf(1, 2, 3)) }
        assertTrue { byteArrayOf(0, 1, 2, 3, 0).matchesRange(1, byteArrayOf(1, 2, 3), 3) }
        assertTrue { byteArrayOf(9, 8, 7, 6).matchesRange(1, byteArrayOf(1, 8, 7, 2), 2, 1, 2) }
        assertFalse { byteArrayOf(1, 2, 3).matchesRange(0, byteArrayOf(1, 2), 3) }
        assertFalse { byteArrayOf(1, 2, 3).matchesRange(0, byteArrayOf(1, 3, 3), 3) }
        assertFailsWith<IndexOutOfBoundsException> {
            byteArrayOf(1, 2, 3).matchesRange(2, byteArrayOf(1, 2, 3), 2, 0, 2)
        }
    }

    @Test
    fun matchPart() {
        assertTrue { byteArrayOf(1, 2, 3).matchesRangePart(0, byteArrayOf(1, 2)) }
        assertTrue { byteArrayOf(1, 2, 3).matchesRangePart(1, byteArrayOf(2, 3)) }
        assertTrue { byteArrayOf(5, 6, 7, 8).matchesRangePart(1, byteArrayOf(0, 6, 7, 9), 3, 1, 2) }
        assertFalse { byteArrayOf(1, 2, 3).matchesRangePart(1, byteArrayOf(3, 3)) }
        assertFailsWith<IndexOutOfBoundsException> {
            byteArrayOf(1, 2, 3).matchesRangePart(2, byteArrayOf(1, 2, 3), 3, 0, 2)
        }
    }

    @Test
    fun nextByteInSameLength() {
        expect("000001") { "000000".hexToByteArray().nextByteInSameLength().toHexString() }
        expect("0001ff") { "0000ff".hexToByteArray().nextByteInSameLength().toHexString() }
        expect("01ffff") { "00ffff".hexToByteArray().nextByteInSameLength().toHexString() }
        expect("ffffff") { "ffffff".hexToByteArray().nextByteInSameLength().toHexString() }
        expect("0000ff") { "0000fe".hexToByteArray().nextByteInSameLength().toHexString() }
    }

    @Test
    fun prevByteInSameLength() {
        assertFailsWith<IllegalStateException> {
            ("000000").hexToByteArray().prevByteInSameLength()
        }

        expect("000000") { "000001".hexToByteArray().prevByteInSameLength().toHexString() }
        expect("0000fe") { "0000ff".hexToByteArray().prevByteInSameLength().toHexString() }
        expect("0000ff") { "000100".hexToByteArray().prevByteInSameLength().toHexString() }
        expect("00ffff") { "010000".hexToByteArray().prevByteInSameLength().toHexString() }
        expect("fffffe") { "ffffff".hexToByteArray().prevByteInSameLength().toHexString() }
        expect("0100ff") { "010100".hexToByteArray().prevByteInSameLength(2).toHexString() }
    }
}
