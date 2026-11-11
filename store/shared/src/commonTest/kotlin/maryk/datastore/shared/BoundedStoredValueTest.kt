package maryk.datastore.shared

import maryk.core.properties.definitions.StringDefinition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class BoundedStoredValueTest {
    @Test fun boundedStringsMatchTheStreamingDecoderAndOwnTheirContent() {
        val definition = StringDefinition()
        for (text in listOf("", "hé\u0000世界🙂", "x".repeat(98_304))) {
            val payload = byteArrayOf(TypeIndicator.NoTypeIndicator.byte) + text.encodeToByteArray()
            val wrapped = ByteArray(5) + payload + ByteArray(7)
            var index = 5
            val generic = readValue(definition, { wrapped[index++] }) { 5 + payload.size - index }
            val decoded = readStoredValue(definition, wrapped, 5, payload.size)
            wrapped.fill(0)
            assertEquals(text, generic)
            assertEquals(text, decoded)
        }
    }

    @Test fun fallbackMarkersAndMalformedSlicesRemainStrict() {
        val definition = StringDefinition()
        assertEquals(null, readStoredValue(definition, byteArrayOf(TypeIndicator.DeletedIndicator.byte), 0, 1))
        val noncanonical = byteArrayOf(0x81.toByte(), 0) + "hello".encodeToByteArray()
        var index = 0
        assertEquals(readValue(definition, { noncanonical[index++] }) { noncanonical.size - index }, readStoredValue(definition, noncanonical, 0, noncanonical.size))
        assertFails { readStoredValue(definition, byteArrayOf(1, 0xc3.toByte(), 0x28), 0, 3) }
        assertFails { readStoredValue(definition, byteArrayOf(1), Int.MAX_VALUE, 1) }
        assertFails { readStoredValue(definition, byteArrayOf(1), 0, 0) }
    }
}
