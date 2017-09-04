package maryk.core.extensions.bytes

import io.kotlintest.matchers.shouldThrow
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
