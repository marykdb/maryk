package maryk.json

import kotlin.test.Test
import kotlin.test.assertFailsWith

internal abstract class AbstractJsonWriterTest {
    abstract fun createJsonWriter(writer: (String) -> Unit): IsJsonLikeWriter

    @Test
    fun notStartWithUnallowedJSONTypes() {
        buildString {
            createJsonWriter {
                append(it)
            }.apply {
                // Should not be able to start with end object
                assertFailsWith<IllegalJsonOperation> {
                    writeEndObject()
                }

                // Should not be able to start with end array
                assertFailsWith<IllegalJsonOperation> {
                    writeEndArray()
                }

                // Should not be able to start with value
                assertFailsWith<IllegalJsonOperation> {
                    writeValue("test")
                }

                // Should not be able to start with string value
                assertFailsWith<IllegalJsonOperation> {
                    writeString("test")
                }

                // Should not be able to start with field name
                assertFailsWith<IllegalJsonOperation> {
                    writeFieldName("test")
                }
            }
        }
    }

    @Test
    fun notAllowIllegalOperationsInsideAnArray() {
        buildString {
            createJsonWriter {
                append(it)
            }.apply {
                writeStartArray()

                // Should not be able to write end object after start array
                assertFailsWith<IllegalJsonOperation> {
                    writeEndObject()
                }

                // Should not be able to write fieldname to array
                assertFailsWith<IllegalJsonOperation> {
                    writeFieldName("test")
                }
            }
        }
    }


    @Test
    fun notAllowIllegalOperationsInsideAnObject() {
        buildString {
            createJsonWriter {
                append(it)
            }.apply {
                writeStartObject()

                // Should not be able to write end array after start object
                assertFailsWith<IllegalJsonOperation> {
                    writeEndArray()
                }

                // Should not be able to write value before a field name
                assertFailsWith<IllegalJsonOperation> {
                    writeValue("false")
                }

                // Should not be able to write string value before a field name
                assertFailsWith<IllegalJsonOperation> {
                    writeString("test")
                }
            }
        }
    }

    @Test
    fun notAllowIllegalOperationsInsideAnObjectFieldName() {
        buildString {
            createJsonWriter {
                append(it)
            }.apply {
                writeStartObject()
                writeFieldName("field")

                // Should not be able to write end array after field name
                assertFailsWith<IllegalJsonOperation> {
                    writeEndArray()
                }

                // Should not be able to write end object after field name
                assertFailsWith<IllegalJsonOperation> {
                    writeEndObject()
                }

                // Should not be able to write field name after field name
                assertFailsWith<IllegalJsonOperation> {
                    writeFieldName("anotherField")
                }
            }
        }
    }
}
