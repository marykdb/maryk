package maryk.core.extensions.bytes

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import org.junit.Test
import kotlin.test.assertEquals

internal class LongKtTest {
    private val longsToTest = longArrayOf(
            Long.MIN_VALUE,
            -4786131286145765123,
            -2222,
            -1,
            0,
            1,
            2222,
            4786131286145765123,
            Long.MAX_VALUE
    )

    @Test
    fun testConversion() {
        longsToTest.forEach {
            initLong(it.toBytes()) shouldBe it
        }
    }

    @Test
    fun testOffsetConversion() {
        longsToTest.forEach {
            val bytes = ByteArray(22)
            assertEquals(
                    it,
                    initLong(it.toBytes(bytes, 10), 10)
            )
        }
    }

    @Test
    fun test7Conversion() {
        longArrayOf(
                MIN_SEVEN_VALUE,
                -999999999L,
                -1L,
                0L,
                1L,
                1504201744L,
                999999999,
                MAX_SEVEN_VALUE
        ).forEach {
            val bytes = ByteArray(22)
            assertEquals(
                    it,
                    initLongSeven(it.toSevenBytes(bytes, 10), 10)
            )
        }
    }

    @Test
    fun testOutOfRange7Conversion() {
        longArrayOf(
                MAX_SEVEN_VALUE + 1,
                MIN_SEVEN_VALUE - 1,
                Long.MAX_VALUE,
                Long.MIN_VALUE
        ).forEach {
            val bytes = ByteArray(22)
            shouldThrow<IllegalArgumentException> {
                initLongSeven(it.toSevenBytes(bytes, 10), 10)
            }
        }
    }

    @Test
    fun test7UnsignedConversion() {
        longArrayOf(
                0L,
                2222L,
                256L*256L*256L*256L*256L*256L*256L-1L
        ).forEach {
            val bytes = ByteArray(22)
            assertEquals(
                    it,
                    initLong(it.toBytes(bytes, 10, 7), 10, 7)
            )
        }
    }

    @Test
    fun testOutOfRange7UnsignedConversion() {
        longArrayOf(
                -1,
                256L*256L*256L*256L*256L*256L*256L,
                Long.MAX_VALUE,
                Long.MIN_VALUE
        ).forEach {
            val bytes = ByteArray(22)
            shouldThrow<IllegalArgumentException> {
                initLong(it.toBytes(bytes, 10, 7), 10, 7)
            }
        }
    }
}