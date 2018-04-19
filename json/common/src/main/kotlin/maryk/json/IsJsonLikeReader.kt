package maryk.json

interface TokenType
@Suppress("unused")
interface ValueType<out T: Any?> : TokenType {
    interface IsNullValueType: ValueType<Nothing>

    object String: ValueType<String>
    object Null: IsNullValueType
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

interface TokenWithType {
    val type: TokenType
}

sealed class JsonToken(val name: String) {
    object StartDocument : JsonToken("StartDocument")

    open class StartObject(override val type: MapType) : JsonToken("StartObject"), TokenWithType
    object SimpleStartObject : StartObject(MapType.Map)

    object EndObject : JsonToken("EndObject")

    open class FieldName(val value: String?) : JsonToken("FieldName")
    object MergeFieldName: FieldName("<<")
    object StartComplexFieldName : JsonToken("StartComplexFieldName")
    object EndComplexFieldName : JsonToken("EndComplexFieldName")

    object ObjectSeparator : JsonToken("ObjectSeparator")
    open class Value<out T: Any?>(
        val value: T,
        override val type: ValueType<T>
    ) : JsonToken("Value"), TokenWithType
    object NullValue: Value<Nothing?>(null, ValueType.Null)

    open class StartArray(override val type: ArrayType) : JsonToken("StartArray"), TokenWithType
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
        this is FieldName -> "$name(${this.value})"
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
    fun skipUntilNextField(handleSkipToken: ((JsonToken) -> Unit)? = null)
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
