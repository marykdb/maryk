package maryk.core.json

interface TokenType
interface ValueType<out T: Any?> : TokenType {
    object String: ValueType<String>
    object Null: ValueType<Nothing>
    object Bool: ValueType<Boolean>
    object Int: ValueType<kotlin.Long>
    object Float: ValueType<kotlin.Double>
}
interface ArrayType : TokenType {
    object Sequence: ArrayType
    object Set: ArrayType
}
interface ObjectType : TokenType {
    object Map: ObjectType
    object OrderedMap: ObjectType
    object Pairs: ObjectType
}

sealed class JsonToken(val name: String) {
    object StartDocument : JsonToken("StartDocument")

    open class StartObject(val type: ObjectType) : JsonToken("StartObject")
    object SimpleStartObject : StartObject(ObjectType.Map)

    object EndObject : JsonToken("EndObject")

    class FieldName(val value: String?) : JsonToken("FieldName")
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

    /** Find the next token */
    fun nextToken(): JsonToken

    /** Skips all JSON values until a next value at same level is discovered */
    fun skipUntilNextField()
}

/** Exception during reading of JSON */
class ExceptionWhileReadingJson internal constructor(): Throwable()

/** Exception for invalid JSON */
open class InvalidJsonContent internal constructor(
    description: String
): Throwable(description)