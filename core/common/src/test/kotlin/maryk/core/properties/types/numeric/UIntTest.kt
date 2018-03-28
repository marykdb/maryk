package maryk.core.properties.types.numeric

import maryk.test.shouldBe
import kotlin.test.Test

internal class UIntTest {
    private val uInt8values = arrayOf(UInt8.MIN_VALUE, UInt8.MAX_VALUE, 89.toUInt8(), 127.toByte().toUInt8())

    @Test
    fun test_equals() {
        3.toUInt8().equals("string") shouldBe false
    }
}
