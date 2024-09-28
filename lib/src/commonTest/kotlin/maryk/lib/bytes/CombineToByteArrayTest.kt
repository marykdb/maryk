package maryk.lib.bytes

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.expect

class CombineToByteArrayTest {
    @Test
    fun testCombineByteArrays() {
        val result = combineToByteArray(
            byteArrayOf(1, 2, 3),
            byteArrayOf(4, 5),
            byteArrayOf(6)
        )
        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5, 6), result)
    }

    @Test
    fun testCombineBytes() {
        val result = combineToByteArray(1.toByte(), 2.toByte(), 3.toByte())
        assertContentEquals(byteArrayOf(1, 2, 3), result)
    }

    @Test
    fun testCombineMixed() {
        val result = combineToByteArray(
            byteArrayOf(1, 2),
            3.toByte(),
            byteArrayOf(4, 5),
            6.toByte()
        )
        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5, 6), result)
    }

    @Test
    fun testEmptyInput() {
        val result = combineToByteArray()
        expect(0) { result.size }
    }

    @Test
    fun testUnsupportedType() {
        assertFailsWith<IllegalArgumentException> {
            combineToByteArray(byteArrayOf(1, 2), "unsupported")
        }
    }
}
