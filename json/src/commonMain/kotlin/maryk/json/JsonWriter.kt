package maryk.json

import maryk.json.JsonEmbedType.ComplexField
import maryk.json.JsonEmbedType.Object
import maryk.json.JsonType.START_ARRAY
import maryk.json.JsonType.START_OBJ

/** A JSON writer which writes to [writer] */
class JsonWriter(
    private val pretty: Boolean = false,
    private val writer: (String) -> Unit
) : AbstractJsonLikeWriter() {
    private val indent = if (pretty) "  " else ""
    private val separator = if (pretty) ", " else ","
    private val colonSpace = if (pretty) ": " else ":"

    override fun writeStartObject(isCompact: Boolean) {
        writeCommaIfNeeded()
        super.writeStartObject(isCompact)
        writer("{")
        makePretty()
    }

    override fun writeEndObject() {
        super.writeEndObject()
        makePretty()
        writer("}")
    }

    override fun writeStartArray(isCompact: Boolean) {
        writeCommaIfNeeded()
        super.writeStartArray(isCompact)
        writer("[")
    }

    override fun writeEndArray() {
        super.writeEndArray()
        writer("]")
    }

    /** Writes the field name for an object */
    override fun writeFieldName(name: String) {
        if (lastType != START_OBJ) {
            writer(",")
            makePretty()
        }
        super.writeFieldName(name)
        writer("\"${escapeJson(name)}\"$colonSpace")
    }

    /** Writes a string value including quotes */
    override fun writeString(value: String) = writeValue("\"${escapeJson(value)}\"")

    /** Writes a value excluding quotes */
    override fun writeValue(value: String) = if (typeStack.isNotEmpty()) {
        when (typeStack.last()) {
            is Object -> {
                super.checkObjectValueAllowed()
                writer(value)
            }
            is JsonEmbedType.Array -> {
                writeCommaIfNeeded()
                super.checkArrayValueAllowed()
                writer(value)
            }
            is ComplexField -> {
                throw JsonWriteException("Complex fields are not possible in JSON")
            }
        }
    } else {
        writer(value)
    }

    private fun writeCommaIfNeeded() {
        if (lastType != START_ARRAY && typeStack.isNotEmpty() && typeStack.last() is JsonEmbedType.Array) {
            writer(separator)
        }
    }

    private fun makePretty() {
        if (pretty) {
            writer("\n${indent.repeat(typeStack.count { it is Object })}")
        }
    }

    private fun escapeJson(value: String): String {
        if (value.isEmpty()) return value
        val builder = StringBuilder(value.length + 8)
        value.forEach { char ->
            when (char) {
                '\\' -> builder.append("\\\\")
                '"' -> builder.append("\\\"")
                '\b' -> builder.append("\\b")
                '\u000C' -> builder.append("\\f")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                else -> {
                    if (char < ' ') {
                        builder.append("\\u")
                        val hex = char.code.toString(16).padStart(4, '0')
                        builder.append(hex)
                    } else {
                        builder.append(char)
                    }
                }
            }
        }
        return builder.toString()
    }
}
