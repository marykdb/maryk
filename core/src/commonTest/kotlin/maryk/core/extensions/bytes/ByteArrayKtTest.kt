package maryk.core.extensions.bytes

import maryk.lib.extensions.compare.compareTo
import maryk.lib.extensions.initByteArrayByHex
import maryk.test.ByteCollector
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.expect

internal class ByteArrayKtTest {
    @OptIn(ExperimentalEncodingApi::class)
    private val bytesToTest = arrayOf(
        Base64.UrlSafe.decode("________"),
        Base64.UrlSafe.decode("AAAAAAA"),
        Base64.UrlSafe.decode("iIiIiIiI"),
        Base64.UrlSafe.decode("iIiIiIiIAAAA__")
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
        assertTrue { initByteArrayByHex("000000").invert().contentEquals(initByteArrayByHex("ffffff")) }
        assertTrue { initByteArrayByHex("d3dfa7").invert().contentEquals(byteArrayOf(44, 32, 88)) }
        assertTrue { initByteArrayByHex("ffffff").invert().contentEquals(initByteArrayByHex("000000")) }
    }
}
