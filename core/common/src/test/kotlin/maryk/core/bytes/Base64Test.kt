package maryk.core.bytes

import maryk.core.extensions.toHex
import maryk.test.shouldBe
import kotlin.test.Test

class Base64Test {
    @Test
    fun fromBase64() {
        Base64.encode(byteArrayOf(0)) shouldBe "AA"
        Base64.encode(byteArrayOf(1)) shouldBe "AQ"
        Base64.encode(byteArrayOf(2)) shouldBe "Ag"
    }

    @Test
    fun toBase64() {
        Base64.decode("AA").toHex() shouldBe "00"
        Base64.decode("7g").toHex() shouldBe "ee"
        Base64.decode("0w").toHex() shouldBe "d3"
        Base64.decode("//").toHex() shouldBe "ff"
    }
}