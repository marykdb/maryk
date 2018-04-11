package maryk.json

/** Describes JSON elements that can be written */
enum class JsonType {
    START, START_OBJ, END_OBJ, FIELD_NAME, OBJ_VALUE, START_ARRAY, END_ARRAY, ARRAY_VALUE
}

/** Class to implement code which is generic among JSON like writers */
abstract class AbstractJsonLikeWriter: IsJsonLikeWriter {
    protected var lastType: JsonType = JsonType.START
    protected var typeStack: MutableList<JsonComplexType> = mutableListOf()

    override fun writeStartObject() {
        typeStack.add(JsonComplexType.OBJECT)
        checkTypeIsAllowed(
            JsonType.START_OBJ,
            arrayOf(JsonType.START, JsonType.FIELD_NAME, JsonType.ARRAY_VALUE, JsonType.START_ARRAY, JsonType.END_OBJ)
        )
    }

    override fun writeEndObject() {
        if(typeStack.isEmpty() || typeStack.last() != JsonComplexType.OBJECT) {
            throw IllegalJsonOperation("There is no object to close")
        }
        typeStack.removeAt(typeStack.lastIndex)
        checkTypeIsAllowed(
            JsonType.END_OBJ,
            arrayOf(JsonType.START_OBJ, JsonType.OBJ_VALUE, JsonType.END_OBJ, JsonType.END_ARRAY)
        )
    }

    override fun writeStartArray() {
        typeStack.add(JsonComplexType.ARRAY)
        checkTypeIsAllowed(
            JsonType.START_ARRAY,
            arrayOf(JsonType.START, JsonType.FIELD_NAME, JsonType.START_ARRAY, JsonType.END_ARRAY)
        )
    }

    override fun writeEndArray() {
        if(typeStack.isEmpty() || typeStack.last() != JsonComplexType.ARRAY) {
            throw IllegalJsonOperation("Json: There is no array to close")
        }
        typeStack.removeAt(typeStack.lastIndex)
        checkTypeIsAllowed(
            JsonType.END_ARRAY,
            arrayOf(JsonType.START_ARRAY, JsonType.ARRAY_VALUE, JsonType.END_ARRAY, JsonType.END_OBJ)
        )
    }

    override fun writeFieldName(name: String) {
        checkTypeIsAllowed(
            JsonType.FIELD_NAME,
            arrayOf(JsonType.START_OBJ, JsonType.OBJ_VALUE, JsonType.END_ARRAY, JsonType.END_OBJ)
        )
    }

    internal fun checkTypeIsAllowed(type: JsonType, allowed: Array<JsonType>){
        if (lastType !in allowed) {
            throw IllegalJsonOperation("Json: $type not allowed after $lastType")
        }
        lastType = type
    }

    protected fun checkObjectOperation() {
        checkTypeIsAllowed(
            JsonType.OBJ_VALUE,
            arrayOf(JsonType.FIELD_NAME)
        )
    }

    protected fun checkArrayOperation() {
        checkTypeIsAllowed(
            JsonType.ARRAY_VALUE,
            arrayOf(JsonType.START_ARRAY, JsonType.ARRAY_VALUE)
        )
    }
}
