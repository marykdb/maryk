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

private val whiteSpaceChars = charArrayOf(' ', '\t', '\n', '\r')
private val numberChars = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
private val skipArray = arrayOf(JsonToken.ObjectSeparator, JsonToken.ArraySeparator, JsonToken.StartJSON)

/** Reads JSON from the supplied [reader] */
class JsonReader(
        private val reader: () -> Char
) : IsJsonLikeReader {
    override var currentToken: JsonToken = JsonToken.StartJSON
    override var lastValue: String = ""
    private val typeStack: MutableList<JsonObjectType> = mutableListOf()
    private var lastChar: Char = ' '

    /** Find the next token */
    override fun nextToken(): JsonToken {
        lastValue = ""
        try {
            when (currentToken) {
                JsonToken.StartJSON -> {
                    lastChar = readSkipWhitespace()
                    when(lastChar) {
                        '{' -> startObject()
                        '[' -> startArray()
                        else -> throwJsonException()
                    }
                }
                JsonToken.StartObject -> {
                    typeStack.add(JsonObjectType.OBJECT)
                    when(lastChar) {
                        '}' -> endObject()
                        '"' -> readFieldName()
                        else -> throwJsonException()
                    }
                }
                JsonToken.EndObject -> {
                    continueComplexRead()
                }
                JsonToken.StartArray -> {
                    typeStack.add(JsonObjectType.ARRAY)
                    if (lastChar == ']') {
                        endArray()
                    } else {
                        readValue(JsonToken.ArrayValue)
                    }
                }
                JsonToken.EndArray -> {
                    continueComplexRead()
                }
                JsonToken.FieldName -> {
                    readValue(JsonToken.ObjectValue)
                }
                JsonToken.ObjectValue -> {
                    readObject()
                }
                JsonToken.ObjectSeparator -> {
                    when(lastChar) {
                        '"' -> readFieldName()
                        else -> throwJsonException()
                    }
                }
                JsonToken.ArrayValue -> {
                    readArray()
                }
                JsonToken.ArraySeparator -> {
                    readValue(JsonToken.ArrayValue)
                }
                is JsonToken.Suspended -> {
                    currentToken = (currentToken as JsonToken.Suspended).lastToken
                    readSkipWhitespace()
                    return nextToken()
                }
                is JsonToken.Stopped -> {
                    return currentToken
                }
            }
        } catch (e: ExceptionWhileReadingJson) {
            currentToken = JsonToken.Suspended(currentToken)
        } catch (e: InvalidJsonContent) {
            currentToken = JsonToken.JsonException(e)
            throw e
        }

        if (currentToken in skipArray) {
            return nextToken()
        }

        return currentToken
    }

    /** Skips all JSON values until a next value at same level is discovered */
    override fun skipUntilNextField() {
        val currentDepth = typeStack.count()
        do {
            nextToken()
        } while (!(currentToken == JsonToken.FieldName && typeStack.count() <= currentDepth))
    }

    private fun read() = try {
        lastChar = reader()
    } catch (e: Throwable) { // Reached end or something bad happened
        throw ExceptionWhileReadingJson()
    }

    private fun readSkipWhitespace(): Char {
        read()
        skipWhiteSpace()
        return lastChar
    }

    private fun skipWhiteSpace() {
        if (lastChar in whiteSpaceChars) {
            readSkipWhitespace() // continue reading
        }
    }

    private fun continueComplexRead() {
        when {
            typeStack.isEmpty() -> currentToken = JsonToken.EndJSON
            else -> when (typeStack.last()) {
                JsonObjectType.OBJECT -> readObject()
                JsonObjectType.ARRAY -> readArray()
            }
        }
    }

    private fun readArray() {
        when(lastChar) {
            ',' -> {
                currentToken = JsonToken.ArraySeparator
                readSkipWhitespace()
            }
            ']' -> endArray()
            else -> throwJsonException()
        }
    }

    private fun readObject() {
        when(lastChar) {
            ',' -> {
                currentToken = JsonToken.ObjectSeparator
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
            '-' -> {
                currentToken = currentValueToken
                readNumber(true)
            }
            in numberChars -> {
                currentToken = currentValueToken
                readNumber(false)
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

    private fun readNumber(startedWithMinus: Boolean) {
        fun addAndAdvance() {
            lastValue += lastChar
            read()
        }

        // Read number
        do {
            addAndAdvance()
        } while (lastChar in numberChars)

        // Check if value starts with illegal 0
        if (startedWithMinus && lastValue.length > 2 && lastValue[1] == '0') {
            throwJsonException()
        } else if (lastValue.length > 1 && lastValue[0] == '0') {
            throwJsonException()
        }

        // Read fraction
        if(lastChar == '.') {
            addAndAdvance()
            if (lastChar !in numberChars) throwJsonException()
            do {
                addAndAdvance()
            } while (lastChar in numberChars)
        }

        // read exponent
        if(lastChar in arrayOf('e', 'E')) {
            addAndAdvance()
            if(lastChar in arrayOf('+', '-')) {
                addAndAdvance()
            }
            if (lastChar !in numberChars) throwJsonException()
            do {
                addAndAdvance()
            } while (lastChar in numberChars)
        }

        skipWhiteSpace()
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
        currentToken = JsonToken.FieldName
        readStringValue()
        if (lastChar != ':') {
            throwJsonException()
        }
        readSkipWhitespace()
    }

    private fun readStringValue() {
        read()
        var skipChar = false
        while(lastChar != '"' || skipChar) {
            lastValue += lastChar
            skipChar = if (lastChar == '\\') !skipChar else false
            read()
        }
        readSkipWhitespace()
    }

    private fun startObject() {
        currentToken = JsonToken.StartObject
        readSkipWhitespace()
    }

    private fun endObject() {
        typeStack.removeAt(typeStack.lastIndex)
        currentToken = JsonToken.EndObject
        if(!typeStack.isEmpty()) {
            readSkipWhitespace()
        }
    }

    private fun startArray() {
        currentToken = JsonToken.StartArray
        readSkipWhitespace()
    }

    private fun endArray() {
        typeStack.removeAt(typeStack.lastIndex)
        currentToken = JsonToken.EndArray
        if(!typeStack.isEmpty()) {
            readSkipWhitespace()
        }
    }

    private fun throwJsonException() {
        throw InvalidJsonContent("Invalid character '$lastChar' after $currentToken")
    }
}
