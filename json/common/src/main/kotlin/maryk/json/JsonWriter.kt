package maryk.json

/** A JSON writer which writes to [writer] */
class JsonWriter(
    private val pretty: Boolean = false,
    private val writer: (String) -> Unit
) : AbstractJsonLikeWriter() {
    override fun writeStartObject() {
        if(lastType != JsonType.START_ARRAY
            && !typeStack.isEmpty()
            && typeStack.last() == JsonComplexType.ARRAY
        ) {
            writer(",")
            if (pretty) { writer(" ") }
        }
        super.writeStartObject()
        writer("{")
        makePretty()
    }

    override fun writeEndObject() {
        super.writeEndObject()
        makePretty()
        writer("}")
    }

    override fun writeStartArray() {
        if(lastType != JsonType.START_ARRAY
            && !typeStack.isEmpty()
            && typeStack.last() == JsonComplexType.ARRAY
        ) {
            writer(",")
            if (pretty) { writer(" ") }
        }
        super.writeStartArray()
        writer("[")
    }

    override fun writeEndArray() {
        super.writeEndArray()
        writer("]")
    }

    /** Writes the field name for an object */
    override fun writeFieldName(name: String) {
        if(lastType != JsonType.START_OBJ) {
            writer(",")
            makePretty()
        }
        super.writeFieldName(name)
        writer("\"$name\":")
        if (pretty) { writer(" ") }
    }

    /** Writes a string value including quotes */
    override fun writeString(value: String) = writeValue("\"$value\"")

    /** Writes a value excluding quotes */
    override fun writeValue(value: String) = if (!typeStack.isEmpty()) {
        when(typeStack.last()) {
            JsonComplexType.OBJECT -> {
                super.checkObjectOperation()
                writer(value)
            }
            JsonComplexType.ARRAY -> {
                if(lastType != JsonType.START_ARRAY) {
                    writer(",")
                    if (pretty) { writer(" ") }
                }
                super.checkArrayOperation()
                writer(value)
            }
        }
    } else {
        throw IllegalJsonOperation("Cannot checkTypeIsAllowed a value outside array or object")
    }

    private fun makePretty() {
        if (pretty) {
            writer("\n")
            for (it in typeStack) {
                if(it == JsonComplexType.OBJECT) { writer("\t") }
            }
        }
    }
}
