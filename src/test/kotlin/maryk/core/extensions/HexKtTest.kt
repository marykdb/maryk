package maryk.core.extensions

import io.kotlintest.matchers.shouldBe
import org.junit.Test

internal class HexKtTest {
    private val bytes = byteArrayOf( 0, -1, 88)

    @Test
    fun testToHexConversion(){
        bytes.toHex() shouldBe "00ff58"
    }

    @Test
    fun testFromHexConversion(){
        bytes contentEquals initByteArrayByHex("00ff58") shouldBe true
    }

    @Test
    fun testHexConversionBothWays(){
        bytes contentEquals initByteArrayByHex(bytes.toHex()) shouldBe true
    }
}