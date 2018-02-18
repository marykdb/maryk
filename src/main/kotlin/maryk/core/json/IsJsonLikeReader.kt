package maryk.core.json

interface JsonTokenIsValue {
    val value: String?
}

sealed class JsonToken(val name: String) {
    object StartJSON : JsonToken("StartJSON")
    object StartObject : JsonToken("StartObject")
    class FieldName(val value: String?) : JsonToken("FieldName")
    object ObjectSeparator : JsonToken("ObjectSeparator")
    class ObjectValue(override val value: String?) : JsonToken("ObjectValue"), JsonTokenIsValue
    object EndObject : JsonToken("EndObject")
    object StartArray : JsonToken("StartArray")
    class ArrayValue(override val value: String?) : JsonToken("ArrayValue"), JsonTokenIsValue
    object ArraySeparator : JsonToken("ArraySeparator")
    object EndArray : JsonToken("EndArray")
    abstract class Stopped(name: String): JsonToken(name)
    object EndJSON : Stopped("EndJSON")
    class Suspended(val lastToken: JsonToken, val storedValue: String?): Stopped("Stopped reader")
    class JsonException(val e: InvalidJsonContent) : Stopped("JsonException")

    override fun toString() = if(this is JsonTokenIsValue) {
        "$name(\"${this.value}\")"
    } else { name }
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