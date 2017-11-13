package maryk.core.extensions.bytes

import maryk.core.properties.ByteCollector
import maryk.test.shouldBe
import kotlin.test.Test

internal class BooleanKtTest {
    private val booleansToTest = booleanArrayOf(
            true,
            false
    )

    @Test
    fun testStreamingConversion() {
        val bc = ByteCollector()
        booleansToTest.forEach {
            bc.reserve(1)
            it.writeBytes(bc::write)

            initBoolean(bc::read) shouldBe it
            bc.reset()
        }
    }
}