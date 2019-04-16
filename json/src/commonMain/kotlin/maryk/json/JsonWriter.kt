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
    override fun writeStartObject(isCompact: Boolean) {
        if (lastType != START_ARRAY
            && typeStack.isNotEmpty()
            && typeStack.last() is JsonEmbedType.Array
        ) {
            writer(",")
            if (pretty) {
                writer(" ")
            }
        }
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
        if (lastType != START_ARRAY
            && typeStack.isNotEmpty()
            && typeStack.last() is JsonEmbedType.Array
        ) {
            writer(",")
            if (pretty) {
                writer(" ")
            }
        }
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
        writer("\"$name\":")
        if (pretty) {
            writer(" ")
        }
    }

    /** Writes a string value including quotes */
    override fun writeString(value: String) = writeValue("\"$value\"")

    /** Writes a value excluding quotes */
    override fun writeValue(value: String) = if (typeStack.isNotEmpty()) {
        when (typeStack.last()) {
            is Object -> {
                super.checkObjectValueAllowed()
                writer(value)
            }
            is JsonEmbedType.Array -> {
                if (lastType != START_ARRAY) {
                    writer(",")
                    if (pretty) {
                        writer(" ")
                    }
                }
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

    private fun makePretty() {
        if (pretty) {
            writer("\n")
            for (it in typeStack) {
                if (it is Object) {
                    writer("\t")
                }
            }
        }
    }
}
