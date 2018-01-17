package maryk.core.json

sealed class JsonToken(val name: String) {
    object StartJSON : JsonToken("StartJSON")
    object StartObject : JsonToken("StartObject")
    object FieldName : JsonToken("FieldName")
    object ObjectSeparator : JsonToken("ObjectSeparator")
    object ObjectValue : JsonToken("ObjectValue")
    object EndObject : JsonToken("EndObject")
    object StartArray : JsonToken("StartArray")
    object ArrayValue : JsonToken("ArrayValue")
    object ArraySeparator : JsonToken("ArraySeparator")
    object EndArray : JsonToken("EndArray")
    abstract class Stopped(name: String): JsonToken(name)
    object EndJSON : Stopped("EndJSON")
    class Suspended(val lastToken: JsonToken): Stopped("Stopped reader")
    class JsonException(val e: InvalidJsonContent) : Stopped("JsonException")
}

/** For JSON like readers to read String based structures. */
interface IsJsonLikeReader {
    var currentToken: JsonToken
    var lastValue: String

    /** Find the next token */
    fun nextToken(): JsonToken

    /** Skips all JSON values until a next value at same level is discovered */
    fun skipUntilNextField()
}

class ExceptionWhileReadingJson : Throwable()

/** Exception for invalid JSON */
class InvalidJsonContent(
    description: String
): Throwable(description)