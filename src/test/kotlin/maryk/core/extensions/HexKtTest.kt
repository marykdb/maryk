package maryk.core.extensions

import maryk.core.properties.exceptions.ParseException
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

internal class HexKtTest {
    private val bytes = byteArrayOf( 0, -1, 88)

    @Test
    fun testToHexConversion(){
        bytes.toHex() shouldBe "00ff58"
    }

    @Test
    fun testFromHexConversion(){
        bytes contentEquals initByteArrayByHex("00ff58") shouldBe true
        bytes contentEquals initByteArrayByHex("00FF58") shouldBe true
    }

    @Test
    fun testHexConversionBothWays(){
        bytes contentEquals initByteArrayByHex(bytes.toHex()) shouldBe true
    }

    @Test
    fun testFromInvalidHexConversion(){
        shouldThrow<ParseException> {
            bytes contentEquals initByteArrayByHex("wrong")
        }
    }
}