package maryk.core.extensions.bytes

import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.expect

internal class BooleanKtTest {
    private val booleansToTest = booleanArrayOf(
        true,
        false
    )

    @Test
    fun testStreamingConversion() {
        val bc = ByteCollector()
        booleansToTest.forEach { boolean ->
            bc.reserve(1)
            boolean.writeBytes(bc::write)

            expect(boolean) { initBoolean(bc::read) }
            bc.reset()
        }
    }
}
