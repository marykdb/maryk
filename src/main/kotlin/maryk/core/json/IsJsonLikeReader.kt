package maryk.core.json

sealed class JsonToken(val name: String) {
    object StartDocument : JsonToken("StartDocument")
    object StartObject : JsonToken("StartObject")
    class FieldName(val value: String?) : JsonToken("FieldName")
    object ObjectSeparator : JsonToken("ObjectSeparator")
    class Value<out T: Any>(val value: T?) : JsonToken("Value")
    object EndObject : JsonToken("EndObject")
    object StartArray : JsonToken("StartArray")
    object ArraySeparator : JsonToken("ArraySeparator")
    object EndArray : JsonToken("EndArray")
    abstract class Stopped(name: String): JsonToken(name)
    object EndDocument : Stopped("EndDocument")
    class Suspended(val lastToken: JsonToken, val storedValue: String?): Stopped("Stopped reader")
    class JsonException(val e: InvalidJsonContent) : Stopped("JsonException")
    override fun toString() = if(this is Value<*>) {
        this.value?.let {
            "$name(\"${this.value}\")"
        } ?: "$name(null)"
    } else if(this is FieldName ) {
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