package maryk.lib.extensions.compare

import maryk.lib.extensions.initByteArrayByHex
import maryk.lib.extensions.toHex
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

class ByteArrayKtTest {

    @Test
    fun matchPart() {
        byteArrayOf(1, 2, 3).matchPart(0, byteArrayOf(1, 2)) shouldBe true
        byteArrayOf(1, 2, 3).matchPart(1, byteArrayOf(2, 3)) shouldBe true
        byteArrayOf(1, 2, 3).matchPart(1, byteArrayOf(3, 3)) shouldBe false
    }

    @Test
    fun nextByteInSameLength() {
        initByteArrayByHex("000000").nextByteInSameLength().toHex() shouldBe "000001"
        initByteArrayByHex("0000ff").nextByteInSameLength().toHex() shouldBe "0001ff"
        initByteArrayByHex("00ffff").nextByteInSameLength().toHex() shouldBe "01ffff"
        initByteArrayByHex("ffffff").nextByteInSameLength().toHex() shouldBe "ffffff"
        initByteArrayByHex("0000fe").nextByteInSameLength().toHex() shouldBe "0000ff"
    }

    @Test
    fun prevByteInSameLength() {
        shouldThrow<IllegalStateException> {
            initByteArrayByHex("000000").prevByteInSameLength()
        }

        initByteArrayByHex("000001").prevByteInSameLength().toHex() shouldBe "000000"
        initByteArrayByHex("0000ff").prevByteInSameLength().toHex() shouldBe "0000fe"
        initByteArrayByHex("000100").prevByteInSameLength().toHex() shouldBe "0000ff"
        initByteArrayByHex("010000").prevByteInSameLength().toHex() shouldBe "00ffff"
        initByteArrayByHex("ffffff").prevByteInSameLength().toHex() shouldBe "fffffe"
    }
}
