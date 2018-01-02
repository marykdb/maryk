package maryk.core.json

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