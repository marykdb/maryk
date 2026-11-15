package maryk.lib.bytes

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.expect
import kotlin.text.CharacterCodingException
import maryk.lib.recyclableByteArray

class StringTest {
    @Test
    fun asciiGroupsPreserveUnicodeAndSurrogateBoundaries() {
        for (length in 0..19) {
            val prefix = "a".repeat(length)
            for (suffix in listOf("", "é", "€", "🙂", "é🙂x")) {
                val text = prefix + suffix
                expect(text.encodeToByteArray().size) { text.calculateUTF8ByteLength() }
            }
            for (surrogate in listOf(0xD800.toChar(), 0xDC00.toChar())) {
                assertFailsWith<IllegalArgumentException> { (prefix + surrogate).calculateUTF8ByteLength() }
            }
        }
    }

    @Test
    fun checkedUtf8LengthsRejectOverflowBeforeWrapping() {
        for (text in listOf("", "ASCII", "é", "ไทย", "😀", "aé😀\u0000")) {
            val length = text.encodeToByteArray().size
            expect(length) { text.calculateUTF8ByteLengthChecked(length) }
            if (length > 0) assertFailsWith<IllegalArgumentException> {
                text.calculateUTF8ByteLengthChecked(length - 1)
            }
        }
        assertFailsWith<IllegalArgumentException> { "\uD800".calculateUTF8ByteLengthChecked() }
        assertFailsWith<IllegalArgumentException> { "\uDC00".calculateUTF8ByteLengthChecked() }
    }

    @Test
    fun boundedUtf8DecodingPreservesStrictValidation() {
        for (padding in 0..19) for (suffix in listOf("", "ASCII\u0000\u007f", "Zoë", "ฉัน", "😃", "\uFEFFstart\uFEFFend")) {
            val text = "a".repeat(padding) + suffix
            val bytes = byteArrayOf(0x80.toByte()) + text.encodeToByteArray() + byteArrayOf(0xff.toByte())
            expect(text) { bytes.decodeUtf8Strict(1, bytes.lastIndex) }
        }
        for (padding in 0..19) for (hex in listOf("80", "c0af", "e080af", "eda080", "f4908080", "c2", "f09f98")) {
            val bytes = ByteArray(padding) { 0x61 } + hex.hexToByteArray()
            assertFailsWith<CharacterCodingException> { bytes.decodeUtf8Strict() }
        }
        assertFailsWith<IllegalArgumentException> { byteArrayOf(1).decodeUtf8Strict(1, 0) }
    }

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
    fun decoderRecoversAfterMalformedInput() {
        assertFailsWith<CharacterCodingException> { byteArrayOf(0x80.toByte()).decodeUtf8Strict() }
        expect("valid 😃") { "valid 😃".encodeToByteArray().decodeUtf8Strict() }
    }

    @Test
    fun publicScratchBufferCannotOverwriteAnActiveDecoder() {
        val bytes = "private decoder".encodeToByteArray()
        var index = 0
        expect("private decoder") {
            initString(bytes.size) {
                recyclableByteArray.fill(0)
                bytes[index++]
            }
        }
    }

    @Test
    fun failedReadersReleaseTheirScratchBuffer() {
        assertFailsWith<IllegalStateException> { initString(3) { error("truncated") } }
        val bytes = "next".encodeToByteArray()
        var index = 0
        expect("next") { initString(bytes.size) { bytes[index++] } }
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
