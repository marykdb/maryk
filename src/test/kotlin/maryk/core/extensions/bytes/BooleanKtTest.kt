package maryk.core.extensions.bytes

import io.kotlintest.matchers.shouldBe
import kotlin.test.Test
import maryk.core.properties.ByteCollector

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