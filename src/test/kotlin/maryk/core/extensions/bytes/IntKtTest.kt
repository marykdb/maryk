package maryk.core.extensions.bytes

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import maryk.core.properties.ByteCollector
import org.junit.Test
import kotlin.test.assertEquals

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

    private val bytes = ByteArray(22)

    @Test
    fun testConversion() {
        intsToTest.forEach {
            assertEquals(
                    it,
                    initInt(it.toBytes())
            )
        }
    }

    @Test
    fun testOffsetConversion() {
        intsToTest.forEach {
            assertEquals(
                    it,
                    initInt(it.toBytes(bytes, 10), 10)
            )
        }
    }

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
    fun test3Conversion() {
        intArrayOf(
                0,
                2222,
                256*256*256-1
        ).forEach {
            assertEquals(
                    it,
                    initInt(it.toBytes(bytes, 10, 3), 10, 3)
            )
        }
    }

    @Test
    fun testOutOfRange3Conversion() {
        intArrayOf(
                Int.MAX_VALUE,
                Int.MIN_VALUE,
                256*256*256,
                -1
        ).forEach {
            shouldThrow<IllegalArgumentException> {
                initInt(it.toBytes(bytes, 10, 3), 10, 3)
            }
        }
    }
}
