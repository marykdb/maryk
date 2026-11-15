package maryk.datastore.indexeddb

import maryk.core.exceptions.StorageException
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.string
import maryk.datastore.shared.TypeIndicator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.text.CharacterCodingException

private object BufferedStringRecord : RootDataModel<BufferedStringRecord>() {
    val text by string(1u)
}

class BufferedStringDecodeTest {
    @Test
    fun bufferedStringsPreserveUnicodeAndByteOrderMarks() {
        for (text in listOf("", "plain text", "\uFEFFretained BOM", "é漢字😀", "é漢字😀".repeat(512), "\uFEFF" + "x".repeat(2048), "x".repeat(65_536))) {
            val encoded = byteArrayOf(TypeIndicator.NoTypeIndicator.byte) + text.encodeToByteArray()
            assertEquals(text, decodeStorageValue(BufferedStringRecord.text.ref(), encoded))
        }
    }

    @Test
    fun invalidUtf8RemainsRejected() {
        for (payload in listOf(
            byteArrayOf(0xC0.toByte(), 0xAF.toByte()),
            byteArrayOf(0xED.toByte(), 0xA0.toByte(), 0x80.toByte()),
            byteArrayOf(0xE2.toByte(), 0x82.toByte()),
            byteArrayOf(0xF4.toByte(), 0x90.toByte(), 0x80.toByte(), 0x80.toByte()),
        )) {
            for (prefixSize in listOf(0, 2048)) {
                val encoded = byteArrayOf(TypeIndicator.NoTypeIndicator.byte) + ByteArray(prefixSize) { 0x61 } + payload
                assertFailsWith<CharacterCodingException> {
                    decodeStorageValue(BufferedStringRecord.text.ref(), encoded)
                }
            }
        }
    }

    @Test
    fun otherIndicatorsAndNoncanonicalPrefixesRetainTheirMeaning() {
        val reference = BufferedStringRecord.text.ref()
        assertNull(decodeStorageValue(reference, byteArrayOf(TypeIndicator.DeletedIndicator.byte)))
        assertEquals(Unit, decodeStorageValue(reference, byteArrayOf(TypeIndicator.EmbedIndicator.byte)))
        assertFailsWith<StorageException> {
            decodeStorageValue(reference, byteArrayOf(TypeIndicator.SimpleTypeIndicator.byte))
        }
        // The generic parser accepts an extended zero type with NoTypeIndicator.
        assertEquals("legacy", decodeStorageValue(reference,
            byteArrayOf(0x81.toByte(), 0) + "legacy".encodeToByteArray()))
    }

    @Test
    fun returnedStringsDoNotAliasTheStorageBuffer() {
        val text = "retained text".repeat(256)
        val encoded = byteArrayOf(TypeIndicator.NoTypeIndicator.byte) + text.encodeToByteArray()
        val decoded = decodeStorageValue(BufferedStringRecord.text.ref(), encoded)
        encoded.fill(0)
        assertEquals(text, decoded)
    }
}
