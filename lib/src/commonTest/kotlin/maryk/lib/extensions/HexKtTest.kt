package maryk.lib.extensions

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.expect

internal class HexKtTest {
    private val bytes = byteArrayOf(0, -1, 88)

    @Test
    fun testToHexConversion() {
        expect("00ff58") { bytes.toHex() }
    }

    @Test
    fun testFromHexConversion() {
        assertTrue { bytes contentEquals initByteArrayByHex("00ff58") }
        assertTrue { bytes contentEquals initByteArrayByHex("00FF58") }
    }

    @Test
    fun testHexConversionBothWays() {
        assertTrue { bytes contentEquals initByteArrayByHex(bytes.toHex()) }
    }

    @Test
    fun testFromInvalidHexConversion() {
        assertFailsWith<NumberFormatException> {
            bytes contentEquals initByteArrayByHex("wrong")
        }
    }
}
