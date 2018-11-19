@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS", "EXPERIMENTAL_API_USAGE")

package maryk.core.extensions.bytes

import maryk.test.ByteCollector
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

internal class UIntKtTest {
    private val intsToTest = uintArrayOf(
        UInt.MIN_VALUE,
        0u,
        1u,
        2222u,
        923587636u,
        UInt.MAX_VALUE
    )

    @Test
    fun testStreamingConversion() {
        val bc = ByteCollector()
        intsToTest.forEach {
            bc.reserve(4)
            it.writeBytes(bc::write)

            initUInt(bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testStreaming3Conversion() {
        val bc = ByteCollector()
        uintArrayOf(
            0u,
            1u,
            2222u,
            0x7FFFFFu
        ).forEach {
            bc.reserve(3)
            it.writeBytes(bc::write, 3)

            initUInt(bc::read, 3) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testOutOfRangeConversion() {
        shouldThrow<IllegalArgumentException> {
            4u.writeBytes({}, 5)
        }
    }
}
