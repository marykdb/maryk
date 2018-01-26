package maryk.core.json

/** A writer which handles JSON like data */
interface IsJsonLikeWriter {
    /** Write Object start */
    fun writeStartObject()
    /** Write Object end */
    fun writeEndObject()

    /** Write Array start */
    fun writeStartArray()
    /** Write Array end */
    fun writeEndArray()

    /** Writes the field [name] for an object */
    fun writeFieldName(name: String)

    /** Writes a String [value] including quotes */
    fun writeString(value: String)

    /** Writes a [value] excluding quotes */
    fun writeValue(value: String)
}

/** Exception for invalid JSON */
class IllegalJsonOperation internal constructor(
    description: String
): Throwable(description)