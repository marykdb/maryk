package maryk.json

import maryk.json.JsonType.ARRAY_VALUE
import maryk.json.JsonType.COMPLEX_FIELD_NAME_END
import maryk.json.JsonType.COMPLEX_FIELD_NAME_START
import maryk.json.JsonType.END_ARRAY
import maryk.json.JsonType.END_OBJ
import maryk.json.JsonType.FIELD_NAME
import maryk.json.JsonType.OBJ_VALUE
import maryk.json.JsonType.START
import maryk.json.JsonType.START_ARRAY
import maryk.json.JsonType.START_OBJ
import maryk.json.JsonType.TAG

/** Describes JSON elements that can be written */
enum class JsonType {
    START,
    START_OBJ,
    END_OBJ,
    FIELD_NAME,
    OBJ_VALUE,
    START_ARRAY,
    END_ARRAY,
    ARRAY_VALUE,
    TAG, // Only available in YAML
    COMPLEX_FIELD_NAME_START, // Only available in YAML
    COMPLEX_FIELD_NAME_END, // Only available in YAML
}

/** Describes JSON complex types */
sealed class JsonEmbedType(val isSimple: Boolean) {
    class Object(isSimple: Boolean): JsonEmbedType(isSimple)
    class Array(isSimple: Boolean): JsonEmbedType(isSimple)
    object ComplexField: JsonEmbedType(false)
}

/** Class to implement code which is generic among JSON like writers */
abstract class AbstractJsonLikeWriter: IsJsonLikeWriter {
    protected var lastType: JsonType = START
    protected var typeStack: MutableList<JsonEmbedType> = mutableListOf()

    override fun writeStartObject(isCompact: Boolean) {
        typeStack.add(JsonEmbedType.Object(isCompact))
        checkTypeIsAllowed(
            START_OBJ,
            arrayOf(
                START,
                FIELD_NAME,
                ARRAY_VALUE,
                START_ARRAY,
                END_OBJ,
                TAG,
                COMPLEX_FIELD_NAME_START,
                COMPLEX_FIELD_NAME_END
            )
        )
    }

    override fun writeEndObject() {
        if(typeStack.isEmpty() || typeStack.last() !is JsonEmbedType.Object) {
            throw IllegalJsonOperation("There is no object to close")
        }
        typeStack.removeAt(typeStack.lastIndex)
        checkTypeIsAllowed(
            END_OBJ,
            arrayOf(START_OBJ, OBJ_VALUE, END_OBJ, END_ARRAY)
        )
    }

    override fun writeStartArray(isCompact: Boolean) {
        typeStack.add(JsonEmbedType.Array(isCompact))
        checkTypeIsAllowed(
            START_ARRAY,
            arrayOf(
                START,
                FIELD_NAME,
                START_ARRAY,
                ARRAY_VALUE,
                END_ARRAY,
                TAG,
                COMPLEX_FIELD_NAME_START,
                COMPLEX_FIELD_NAME_END
            )
        )
    }

    override fun writeEndArray() {
        if(typeStack.isEmpty() || typeStack.last() !is JsonEmbedType.Array) {
            throw IllegalJsonOperation("Json: There is no array to close")
        }
        typeStack.removeAt(typeStack.lastIndex)
        checkTypeIsAllowed(
            END_ARRAY,
            arrayOf(START_ARRAY, ARRAY_VALUE, END_ARRAY, END_OBJ, TAG)
        )
    }

    override fun writeFieldName(name: String) {
        checkTypeIsAllowed(
            FIELD_NAME,
            arrayOf(START_OBJ, OBJ_VALUE, END_ARRAY, END_OBJ)
        )
    }

    protected fun checkTypeIsAllowed(type: JsonType, allowed: Array<JsonType>){
        if (lastType !in allowed) {
            throw IllegalJsonOperation("Type $type not allowed after $lastType")
        }
        lastType = type
    }

    /** For writing values in Objects */
    protected fun checkObjectValueAllowed() {
        checkTypeIsAllowed(
            OBJ_VALUE,
            arrayOf(FIELD_NAME, TAG, COMPLEX_FIELD_NAME_END)
        )
    }

    /** For writing values in Arrays */
    protected fun checkArrayValueAllowed() {
        checkTypeIsAllowed(
            ARRAY_VALUE,
            arrayOf(START_ARRAY, ARRAY_VALUE, TAG)
        )
    }
}
