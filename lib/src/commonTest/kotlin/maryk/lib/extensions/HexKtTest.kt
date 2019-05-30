package maryk.lib.extensions

import maryk.lib.exceptions.ParseException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.expect

internal class HexKtTest {
    private val bytes = byteArrayOf(0, -1, 88)

    @Test
    fun testToHexConversion() {
        expect("00ff58") { bytes.toHex() }

        expect("ff58") { byteArrayOf(0, 0, 0, -1, 88).toHex(true) }
        expect("044d") { byteArrayOf(4, 77).toHex(true) }
        expect("") { byteArrayOf(0, 0, 0).toHex(true) }
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
        assertFailsWith<ParseException> {
            bytes contentEquals initByteArrayByHex("wrong")
        }
    }
}
