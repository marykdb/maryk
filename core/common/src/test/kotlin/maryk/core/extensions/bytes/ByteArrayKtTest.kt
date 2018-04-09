package maryk.core.extensions.bytes

import maryk.core.properties.ByteCollector
import maryk.lib.bytes.Base64
import maryk.lib.extensions.compare.compareTo
import maryk.test.shouldBe
import kotlin.test.Test

internal class ByteArrayKtTest {
    private val bytesToTest = arrayOf(
        Base64.decode("////////"),
        Base64.decode("AAAAAAA"),
        Base64.decode("iIiIiIiI"),
        Base64.decode("iIiIiIiIAAAA//")
    )

    @Test
    fun testStreamingConversion() {
        val bc = ByteCollector()
        bytesToTest.forEach {
            bc.reserve(it.size)
            it.writeBytes(bc::write)

            initByteArray(it.size, bc::read).compareTo(it) shouldBe 0
            bc.reset()
        }
    }
}
