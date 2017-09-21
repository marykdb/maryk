package maryk.core.extensions.bytes

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import maryk.core.extensions.toHex
import maryk.core.properties.ByteCollector
import org.junit.Test

internal class IntKtTest {
    private val intsToTest = intArrayOf(
            Int.MIN_VALUE,
            -1933587636,
            -2222,
            -1,
            0,
            1,
            2222,
            923587636,
            Int.MAX_VALUE
    )

    @Test
    fun testStreamingConversion() {
        val bc = ByteCollector()
        intsToTest.forEach {
            bc.reserve(4)
            it.writeBytes(bc::write)

            initInt(bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testStreaming3Conversion() {
        val bc = ByteCollector()
        intArrayOf(
                -0x7FFFFF
                -1,
                0,
                1,
                2222,
                0x7FFFFF
        ).forEach {
            bc.reserve(3)
            it.writeBytes(bc::write, 3)

            initInt(bc::read, 3) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testOutOfRangeConversion() {
        shouldThrow<IllegalArgumentException> {
            4.writeBytes({}, 5)
        }
    }

    @Test
    fun testStreamingVarIntConversion() {
        val bc = ByteCollector()

        testByteContent(bc, 2222, "ae11")
        testByteContent(bc, -2222, "d2eeffff0f")
        testByteContent(bc, 1, "01")
        testByteContent(bc, 0, "00")
        testByteContent(bc, -1, "ffffffff0f")
        testByteContent(bc, -1933587636, "cc96ffe508")
        testByteContent(bc, 923587636, "b4a8b3b803")
        testByteContent(bc, Int.MAX_VALUE, "ffffffff07")
        testByteContent(bc, Int.MIN_VALUE, "8080808008")
    }

    private fun testByteContent(bc: ByteCollector, it: Int, hexValue: String) {
        bc.reserve(it.computeVarByteSize())
        it.writeVarBytes(bc::write)

        initIntByVar(bc::read) shouldBe it

        bc.bytes!!.toHex() shouldBe hexValue
        bc.reset()
    }
}
