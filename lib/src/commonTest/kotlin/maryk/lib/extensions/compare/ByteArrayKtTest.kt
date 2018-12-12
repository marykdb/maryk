package maryk.lib.extensions.compare

import maryk.test.shouldBe
import kotlin.test.Test

class ByteArrayKtTest {

    @Test
    fun matchPart() {
        byteArrayOf(1, 2, 3).matchPart(0, byteArrayOf(1, 2)) shouldBe true
        byteArrayOf(1, 2, 3).matchPart(1, byteArrayOf(2, 3)) shouldBe true
        byteArrayOf(1, 2, 3).matchPart(1, byteArrayOf(3, 3)) shouldBe false
    }
}
