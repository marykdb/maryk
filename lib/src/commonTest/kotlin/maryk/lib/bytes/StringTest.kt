package maryk.lib.bytes

import maryk.lib.extensions.initByteArrayByHex
import maryk.lib.extensions.toHex
import maryk.test.shouldBe
import kotlin.test.Test

class StringTest {
    private val stringsAndBytes = mapOf(
        "546573744d6172796b4d6f64656c" to "TestMarykModel",
        "e0b889e0b8b1e0b899e0b89fe0b8b1e0b887e0b984e0b8a1e0b988e0b980e0b882e0b989e0b8b2e0b983e0b888" to "ฉันฟังไม่เข้าใจ"
    )

    @Test
    fun testBytesToString() {
        for ((hex, value) in stringsAndBytes) {
            val b = initByteArrayByHex(hex)

            var i = 0
            initString(b.size) {
                b[i++]
            } shouldBe value
        }
    }

    @Test
    fun testStringToBytes() {
        for ((hex, value) in stringsAndBytes) {
            val size = value.calculateUTF8ByteLength()

            val b = ByteArray(size)
            var i = 0

            value.writeUTF8Bytes {
                b[i++] = it
            }

            b.toHex() shouldBe hex
        }
    }
}
