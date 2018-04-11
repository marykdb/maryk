package maryk.json

interface TokenType
@Suppress("unused")
interface ValueType<out T: Any?> : TokenType {
    object String: ValueType<String>
    object Null: ValueType<Nothing>
    object Bool: ValueType<Boolean>
    object Int: ValueType<Long>
    object Float: ValueType<Double>
}
interface ArrayType : TokenType {
    object Sequence: ArrayType
    object Set: ArrayType
}
interface MapType : TokenType {
    object Map: MapType
    object OrderedMap: MapType
    object Pairs: MapType
}

sealed class JsonToken(val name: String) {
    object StartDocument : JsonToken("StartDocument")

    open class StartObject(val type: MapType) : JsonToken("StartObject")
    object SimpleStartObject : StartObject(MapType.Map)

    object EndObject : JsonToken("EndObject")

    open class FieldName(val value: String?) : JsonToken("FieldName")
    object MergeFieldName: FieldName("<<")
    object StartComplexFieldName : JsonToken("StartComplexFieldName")
    object EndComplexFieldName : JsonToken("EndComplexFieldName")

    object ObjectSeparator : JsonToken("ObjectSeparator")
    class Value<out T: Any?>(val value: T, val type: ValueType<T>) : JsonToken("Value")

    open class StartArray(val type: ArrayType) : JsonToken("StartArray")
    object SimpleStartArray : StartArray(type = ArrayType.Sequence)

    object ArraySeparator : JsonToken("ArraySeparator")
    object EndArray : JsonToken("EndArray")

    abstract class Stopped(name: String): JsonToken(name)
    object EndDocument : Stopped("EndDocument")
    class Suspended(val lastToken: JsonToken, val storedValue: String?): Stopped("Stopped reader")
    class JsonException(val e: InvalidJsonContent) : Stopped("JsonException")
    override fun toString() = when {
        this is Value<*> -> this.value.let {
            "$name(${this.value})"
        }
        this is FieldName -> "$name(\"${this.value}\")"
        else -> name
    }
}

/** For JSON like readers to read String based structures. */
interface IsJsonLikeReader {
    var currentToken: JsonToken
    var columnNumber: Int
    var lineNumber: Int

    /** Find the next token */
    fun nextToken(): JsonToken

    /** Skips all JSON values until a next value at same level is discovered */
    fun skipUntilNextField()
}

/** Exception during reading of JSON */
class ExceptionWhileReadingJson: Throwable()

/** Exception for invalid JSON */
open class InvalidJsonContent(
    description: String
): Throwable(description) {
    var lineNumber: Int? = null
    var columnNumber: Int? = null

    override val message: String?
        get() = "[l: $lineNumber, c: $columnNumber] ${super.message}"
}
