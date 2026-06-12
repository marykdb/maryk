package io.maryk.app.data

import maryk.json.JsonReader
import maryk.json.JsonToken
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertIs

class DataImportJsonTest {
    @Test
    fun acceptsWhitespaceAfterJsonRecords() {
        val reader = readerAfterEmptyArray("[ ] \n")
        ensureJsonDocumentEnded(reader)
    }

    @Test
    fun rejectsTrailingJsonAfterRecords() {
        val reader = readerAfterEmptyArray("[ ] {}")
        assertFails {
            ensureJsonDocumentEnded(reader)
        }
    }

    @Test
    fun rejectsTrailingTextAfterRecords() {
        val reader = readerAfterEmptyArray("[ ] trailing")
        assertFails {
            ensureJsonDocumentEnded(reader)
        }
    }

    @Test
    fun acceptsWhitespaceAfterSingleJsonRecord() {
        val reader = readerAfterEmptyObject("{ } \n")
        ensureJsonDocumentEnded(reader)
    }

    @Test
    fun rejectsTrailingTextAfterSingleJsonRecord() {
        val reader = readerAfterEmptyObject("{ } trailing")
        assertFails {
            ensureJsonDocumentEnded(reader)
        }
    }
}

private fun readerAfterEmptyArray(content: String): JsonReader {
    val reader = jsonReader(content)
    assertIs<JsonToken.StartArray>(reader.nextToken())
    assertIs<JsonToken.EndArray>(reader.nextToken())
    return reader
}

private fun readerAfterEmptyObject(content: String): JsonReader {
    val reader = jsonReader(content)
    assertIs<JsonToken.StartObject>(reader.nextToken())
    assertIs<JsonToken.EndObject>(reader.nextToken())
    return reader
}

private fun jsonReader(content: String): JsonReader {
    val iterator = content.iterator()
    return JsonReader { if (iterator.hasNext()) iterator.nextChar() else null }
}
