package maryk.core.json

sealed class JsonToken(val name: String) {
    object START_JSON : JsonToken("START_JSON")
    object START_OBJECT: JsonToken("START_OBJECT")
    object FIELD_NAME: JsonToken("FIELD_NAME")
    object OBJECT_SEPARATOR : JsonToken("OBJECT_SEPARATOR")
    object OBJECT_VALUE : JsonToken("OBJECT_VALUE")
    object END_OBJECT: JsonToken("END_OBJECT")
    object START_ARRAY: JsonToken("START_ARRAY")
    object ARRAY_VALUE : JsonToken("ARRAY_VALUE")
    object ARRAY_SEPARATOR : JsonToken("ARRAY_SEPARATOR")
    object END_ARRAY: JsonToken("END_ARRAY")
    object END_JSON: JsonToken("END_JSON")
    class SUSPENDED(val lastToken: JsonToken): JsonToken("Stopped reader")
}

private val whiteSpaceChars = charArrayOf(' ', '\t', '\n', '\r')
private val numberChars = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
private val skipArray = arrayOf(JsonToken.OBJECT_SEPARATOR, JsonToken.ARRAY_SEPARATOR)

/** Parses JSON
 * @param optimized true to parse with CPU optimized format
 * @param reader to read json from
 */
class JsonParser(
        val optimized: Boolean = false,
        val reader: () -> Char
) {
    var currentToken: JsonToken = JsonToken.START_JSON
    var lastValue: String = ""
    private var typeStack: MutableList<JsonObjectType> = mutableListOf()
    private var lastChar: Char = readSkipWhitespace()

    init {
        nextToken()
    }

    /** Find the next token */
    fun nextToken(): JsonToken {
        lastValue = ""
        try {
            when (currentToken) {
                JsonToken.START_JSON -> when(lastChar) {
                    '{' -> startObject()
                    '[' -> startArray()
                    else -> throwJsonException()
                }
                JsonToken.START_OBJECT -> {
                    typeStack.add(JsonObjectType.OBJECT)
                    when(lastChar) {
                        '}' -> endObject()
                        '"' -> readFieldName()
                        else -> throwJsonException()
                    }
                }
                JsonToken.END_OBJECT -> {
                    continueComplexRead()
                }
                JsonToken.START_ARRAY -> {
                    typeStack.add(JsonObjectType.ARRAY)
                    if (lastChar == ']') {
                        endArray()
                    } else {
                        readValue(JsonToken.ARRAY_VALUE)
                    }
                }
                JsonToken.END_ARRAY -> {
                    continueComplexRead()
                }
                JsonToken.FIELD_NAME -> {
                    readValue(JsonToken.OBJECT_VALUE)
                }
                JsonToken.OBJECT_VALUE -> {
                    readObject()
                }
                JsonToken.OBJECT_SEPARATOR -> {
                    when(lastChar) {
                        '"' -> readFieldName()
                        else -> throwJsonException()
                    }
                }
                JsonToken.ARRAY_VALUE -> {
                    readArray()
                }
                JsonToken.ARRAY_SEPARATOR -> {
                    if (lastChar == ']') {
                        endArray()
                    } else {
                        readValue(JsonToken.ARRAY_VALUE)
                    }
                }
            }
        } catch (e: ExceptionWhileReadingJson) {
            currentToken = JsonToken.SUSPENDED(currentToken)
        }

        if (currentToken in skipArray) {
            return nextToken()
        }

        return currentToken
    }

    /** Method that walks the JSON until a next value at same level is discovered */
    fun ignoreUntilNextField() {
        val currentDepth = typeStack.count()
        do {
            nextToken()
        } while (!(currentToken == JsonToken.FIELD_NAME && typeStack.count() <= currentDepth))
    }

    private fun read() = try {
        lastChar = reader()
    } catch (e: Throwable) { // Reached end or something bad happened
        throw ExceptionWhileReadingJson()
    }

    private fun readSkipWhitespace(): Char {
        read()
        if (lastChar in whiteSpaceChars) {
            readSkipWhitespace() // continue reading
        }
        return lastChar
    }

    private fun continueComplexRead() {
        when {
            typeStack.isEmpty() -> currentToken = JsonToken.END_JSON
            else -> when (typeStack.last()) {
                JsonObjectType.OBJECT -> readObject()
                JsonObjectType.ARRAY -> readArray()
            }
        }
    }

    private fun readArray() {
        when(lastChar) {
            ',' -> {
                currentToken = JsonToken.ARRAY_SEPARATOR
                readSkipWhitespace()
            }
            ']' -> endArray()
            else -> throwJsonException()
        }
    }

    private fun readObject() {
        when(lastChar) {
            ',' -> {
                currentToken = JsonToken.OBJECT_SEPARATOR
                readSkipWhitespace()
            }
            '}' -> endObject()
            else -> throwJsonException()
        }
    }

    private fun readValue(currentValueToken: JsonToken) {
        when (lastChar) {
            '{' -> startObject()
            '[' -> startArray()
            '"' -> {
                currentToken = currentValueToken
                readStringValue()
            }
            '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                currentToken = currentValueToken
                readNumber()
            }
            'n' -> {
                currentToken = currentValueToken
                readNullValue()
            }
            't' -> {
                currentToken = currentValueToken
                readTrue()
            }
            'f' -> {
                currentToken = currentValueToken
                readFalse()
            }
            else -> throwJsonException()
        }
    }

    private fun readNumber() {
        do {
            lastValue += lastChar
            read()
        } while (lastChar in numberChars)
    }

    private fun readFalse() {
        ("alse").forEach {
            read()
            if(lastChar != it) {
                throwJsonException()
            }
        }
        lastValue = "false"
        readSkipWhitespace()
    }

    private fun readTrue() {
        ("rue").forEach {
            read()
            if(lastChar != it) {
                throwJsonException()
            }
        }
        lastValue = "true"
        readSkipWhitespace()
    }

    private fun readNullValue() {
        ("ull").forEach {
            read()
            if(lastChar != it) {
                throwJsonException()
            }
        }
        lastValue = "null"
        readSkipWhitespace()
    }

    private fun readFieldName() {
        currentToken = JsonToken.FIELD_NAME
        readStringValue()
        if (lastChar != ':') {
            throwJsonException()
        }
        readSkipWhitespace()
    }

    private fun readStringValue() {
        read()
        var skipChar = false
        lastValue = ""
        while(lastChar != '"' || skipChar) {
            lastValue += lastChar
            skipChar = if (lastChar == '\\') !skipChar else false
            read()
        }
        readSkipWhitespace()
    }

    private fun startObject() {
        currentToken = JsonToken.START_OBJECT
        readSkipWhitespace()
    }

    private fun endObject() {
        typeStack.removeAt(typeStack.lastIndex)
        currentToken = JsonToken.END_OBJECT
        if(!typeStack.isEmpty()) {
            readSkipWhitespace()
        }
    }

    private fun startArray() {
        currentToken = JsonToken.START_ARRAY
        readSkipWhitespace()
    }

    private fun endArray() {
        typeStack.removeAt(typeStack.lastIndex)
        currentToken = JsonToken.END_ARRAY
        if(!typeStack.isEmpty()) {
            readSkipWhitespace()
        }
    }

    private fun throwJsonException() {
        throw InvalidJsonContent("Invalid character '$lastChar' after $currentToken")
    }
}

class ExceptionWhileReadingJson : Throwable()

/** Exception for invalid JSON */
class InvalidJsonContent(
        description: String
): Throwable(description)