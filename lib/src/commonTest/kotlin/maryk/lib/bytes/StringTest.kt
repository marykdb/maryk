package maryk.lib.bytes

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.expect
import kotlin.text.CharacterCodingException
import maryk.lib.recyclableByteArray

class StringTest {
    @Test
    fun malformedUtf8IsRejectedAtEveryBufferSize() {
        val malformed = listOf("80", "c0af", "e080af", "eda080", "f4908080", "c2", "f09f98")
        for (padding in listOf(0, recyclableByteArray.size + 1)) for (hex in malformed) {
            val bytes = ByteArray(padding) { 0x61 } + hex.hexToByteArray()
            var index = 0
            assertFailsWith<CharacterCodingException>("padding=$padding bytes=$hex") {
                initString(bytes.size) { bytes[index++] }
            }
        }
    }

    @Test
    fun byteSuppliersCanDecodeNestedStrings() {
        val outer = "outer text".encodeToByteArray()
        val inner = "inner".encodeToByteArray()
        for (nestedAt in listOf(0, 1, inner.size, outer.lastIndex)) {
            var index = 0
            expect("outer text") {
                initString(outer.size) {
                    if (index == nestedAt) {
                        var nestedIndex = 0
                        expect("inner") { initString(inner.size) { inner[nestedIndex++] } }
                    }
                    outer[index++]
                }
            }
        }
    }

    private val stringsAndBytes = mapOf(
        "546573744d6172796b4d6f64656c" to "TestMarykModel",
        "e0b889e0b8b1e0b899e0b89fe0b8b1e0b887e0b984e0b8a1e0b988e0b980e0b882e0b989e0b8b2e0b983e0b888" to "ฉันฟังไม่เข้าใจ"
    )

    @Test
    fun testBytesWithReaderToString() {
        for ((hex, value) in stringsAndBytes) {
            val b = (hex).hexToByteArray()

            var i = 0
            expect(value) {
                initString(b.size) {
                    b[i++]
                }
            }
        }
    }

    @Test
    fun testNegativeLengthFails() {
        assertFailsWith<IllegalArgumentException> {
            initString(-1) { 0 }
        }
    }

    @Test
    fun testLargeLengthReadsBeforeGrowingToDeclaredSize() {
        var reads = 0
        assertFailsWith<IllegalStateException> {
            initString(Int.MAX_VALUE) {
                reads++
                if (reads > 1) {
                    throw IllegalStateException("eof")
                }
                0x41
            }
        }
        expect(2) { reads }
    }

    @Test
    fun testStringToBytes() {
        for ((hex, value) in stringsAndBytes) {
            val size = value.calculateUTF8ByteLength()

            val b = ByteArray(size)
            var i = 0

            value.writeUTF8Bytes {
                b[i++] = it
            }

            expect(hex) { b.toHexString() }
        }
    }

    @Test
    fun testCodePointToString() {
        expect("A") { fromCodePoint(0x41) }
        expect("😃") { fromCodePoint(0x1F603) }
    }

    @Test
    fun testCodePointRejectsInvalidScalars() {
        assertFailsWith<IllegalArgumentException> {
            fromCodePoint(-1)
        }
        assertFailsWith<IllegalArgumentException> {
            fromCodePoint(0xD800)
        }
        assertFailsWith<IllegalArgumentException> {
            fromCodePoint(0xDFFF)
        }
        assertFailsWith<IllegalArgumentException> {
            fromCodePoint(0x110000)
        }
    }

    @Test
    fun testUnpairedHighSurrogateFails() {
        val value = charArrayOf(0xD83D.toChar()).concatToString()
        expect(0xD83D) { value.single().code }
        assertFailsWith<IllegalArgumentException> {
            value.calculateUTF8ByteLength()
        }
        assertFailsWith<IllegalArgumentException> {
            value.writeUTF8Bytes { }
        }
    }

    @Test
    fun testUnexpectedLowSurrogateFails() {
        val value = charArrayOf(0xDC00.toChar()).concatToString()
        expect(0xDC00) { value.single().code }
        assertFailsWith<IllegalArgumentException> {
            value.calculateUTF8ByteLength()
        }
        assertFailsWith<IllegalArgumentException> {
            value.writeUTF8Bytes { }
        }
    }
}
