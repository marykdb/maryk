package maryk.core.extensions.bytes

import maryk.core.base64.Base64Maryk
import maryk.lib.extensions.compare.compareTo
import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.expect

internal class ByteArrayKtTest {
    private val bytesToTest = arrayOf(
        Base64Maryk.decode("________"),
        Base64Maryk.decode("AAAAAAA"),
        Base64Maryk.decode("iIiIiIiI"),
        Base64Maryk.decode("iIiIiIiIAAAA_w")
    )

    @Test
    fun testStreamingConversion() {
        val bc = ByteCollector()
        bytesToTest.forEach {
            bc.reserve(it.size)
            it.writeBytes(bc::write)

            expect(0) { initByteArray(it.size, bc::read) compareTo it }
            bc.reset()
        }
    }

    @Test
    fun invertBytes() {
        assertTrue { "000000".hexToByteArray().invert().contentEquals("ffffff".hexToByteArray()) }
        assertTrue { "d3dfa7".hexToByteArray().invert().contentEquals(byteArrayOf(44, 32, 88)) }
        assertTrue { "ffffff".hexToByteArray().invert().contentEquals("000000".hexToByteArray()) }
    }
}
