package io.maryk.cli

import maryk.json.JsonReader
import maryk.json.JsonToken
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class RecordValueReaderTest {
    @Test
    fun jsonInputEndAllowsWhitespaceOnly() {
        val reader = readerAfterEmptyObject("{} \n")
        ensureJsonInputEnded(reader)
    }

    @Test
    fun jsonInputEndRejectsTrailingToken() {
        val reader = readerAfterEmptyObject("{} []")
        assertFails {
            ensureJsonInputEnded(reader)
        }
    }

    @Test
    fun protoInputReaderRejectsTrailingBytes() {
        assertFailsWith<IllegalArgumentException> {
            readProtoInputPayload(byteArrayOf(1, 2), "test") { reader ->
                reader()
            }
        }
    }

    @Test
    fun protoInputReaderRejectsOverread() {
        assertFailsWith<IllegalArgumentException> {
            readProtoInputPayload(byteArrayOf(1), "test") { reader ->
                reader()
                reader()
            }
        }
    }
}

private fun readerAfterEmptyObject(content: String): JsonReader {
    val iterator = content.iterator()
    val reader = JsonReader { if (iterator.hasNext()) iterator.nextChar() else null }
    assertIs<JsonToken.StartObject>(reader.nextToken())
    assertIs<JsonToken.EndObject>(reader.nextToken())
    return reader
}
