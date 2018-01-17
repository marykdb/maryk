package maryk.core.json

interface IsJsonLikeWriter {
    fun writeStartObject()
    fun writeEndObject()

    fun writeStartArray()
    fun writeEndArray()

    /** Writes the field name for an object */
    fun writeFieldName(name: String)

    /** Writes a string value including quotes */
    fun writeString(value: String)

    /** Writes a value excluding quotes */
    fun writeValue(value: String)
}

/** Exception for invalid JSON */
class IllegalJsonOperation(
    description: String
): Throwable(description)