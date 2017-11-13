package maryk.core.extensions.bytes

import maryk.core.bytes.Base64
import maryk.core.extensions.compare.compareTo
import maryk.core.properties.ByteCollector
import maryk.test.shouldBe
import kotlin.test.Test

internal class ByteArrayKtTest {
    private val bytesToTest = arrayOf(
            Base64.decode("________"),
            Base64.decode("AAAAAAA"),
            Base64.decode("iIiIiIiI"),
            Base64.decode("iIiIiIiIAAAA__")
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