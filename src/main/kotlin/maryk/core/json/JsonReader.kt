package maryk.core.json

import maryk.core.extensions.HEX_CHARS

private val numberChars = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
private val skipArray = arrayOf(JsonToken.ObjectSeparator, JsonToken.ArraySeparator, JsonToken.StartJSON)

/** Reads JSON from the supplied [reader] */
class JsonReader(
    private val reader: () -> Char
) : IsJsonLikeReader {
    override var currentToken: JsonToken = JsonToken.StartJSON

    private var storedValue: String? = ""
    private val typeStack: MutableList<JsonComplexType> = mutableListOf()
    private var lastChar: Char = ' '

    override fun nextToken(): JsonToken {
        storedValue = ""
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
                    typeStack.add(JsonComplexType.OBJECT)
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
                    typeStack.add(JsonComplexType.ARRAY)
                    if (lastChar == ']') {
                        endArray()
                    } else {
                        readValue({ JsonToken.ArrayValue(it) })
                    }
                }
                JsonToken.EndArray -> {
                    continueComplexRead()
                }
                is JsonToken.FieldName -> {
                    readValue({ JsonToken.ObjectValue(it) })
                }
                is JsonToken.ObjectValue -> {
                    readObject()
                }
                JsonToken.ObjectSeparator -> {
                    when(lastChar) {
                        '"' -> readFieldName()
                        else -> throwJsonException()
                    }
                }
                is JsonToken.ArrayValue -> {
                    readArray()
                }
                JsonToken.ArraySeparator -> {
                    readValue({ JsonToken.ArrayValue(it) })
                }
                is JsonToken.Suspended -> {
                    (currentToken as JsonToken.Suspended).let {
                        currentToken = it.lastToken
                        storedValue = it.storedValue
                    }
                    readSkipWhitespace()
                    return nextToken()
                }
                is JsonToken.Stopped -> {
                    return currentToken
                }
            }
        } catch (e: ExceptionWhileReadingJson) {
            currentToken = JsonToken.Suspended(currentToken, storedValue)
        } catch (e: InvalidJsonContent) {
            currentToken = JsonToken.JsonException(e)
            throw e
        }

        if (currentToken in skipArray) {
            return nextToken()
        }

        return currentToken
    }

    override fun skipUntilNextField() {
        val currentDepth = typeStack.count()
        do {
            nextToken()
        } while (!(currentToken is JsonToken.FieldName && typeStack.count() <= currentDepth))
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
        if (lastChar.isWhitespace()) {
            readSkipWhitespace() // continue reading
        }
    }

    private fun continueComplexRead() {
        when {
            typeStack.isEmpty() -> currentToken = JsonToken.EndJSON
            else -> when (typeStack.last()) {
                JsonComplexType.OBJECT -> readObject()
                JsonComplexType.ARRAY -> readArray()
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

    private fun readValue(currentTokenCreator: (value: String?) -> JsonToken) {
        when (lastChar) {
            '{' -> startObject()
            '[' -> startArray()
            '"' -> readStringValue(currentTokenCreator)
            '-' -> readNumber(true, currentTokenCreator)
            in numberChars -> readNumber(false, currentTokenCreator)
            'n' -> readNullValue(currentTokenCreator)
            't' -> readTrue(currentTokenCreator)
            'f' -> readFalse(currentTokenCreator)
            else -> throwJsonException()
        }
    }

    private fun readNumber(startedWithMinus: Boolean, currentTokenCreator: (value: String?) -> JsonToken) {
        fun addAndAdvance() {
            storedValue += lastChar
            read()
        }

        // Read number
        do {
            addAndAdvance()
        } while (lastChar in numberChars)

        // Check if value starts with illegal 0
        storedValue?.let {
            if (startedWithMinus && it.length > 2 && it[1] == '0') {
                throwJsonException()
            } else if (it.length > 1 && it[0] == '0') {
                throwJsonException()
            }
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

        currentToken = currentTokenCreator(storedValue)

        skipWhiteSpace()
    }

    private fun readFalse(currentTokenCreator: (value: String?) -> JsonToken) {
        for (it in "alse") {
            read()
            if(lastChar != it) {
                throwJsonException()
            }
        }
        storedValue = "false"

        currentToken = currentTokenCreator(storedValue)

        readSkipWhitespace()
    }

    private fun readTrue(currentTokenCreator: (value: String?) -> JsonToken) {
        ("rue").forEach {
            read()
            if(lastChar != it) {
                throwJsonException()
            }
        }
        storedValue = "true"

        currentToken = currentTokenCreator(storedValue)

        readSkipWhitespace()
    }

    private fun readNullValue(currentTokenCreator: (value: String?) -> JsonToken) {
        for (it in "ull") {
            read()
            if(lastChar != it) {
                throwJsonException()
            }
        }
        storedValue = null

        currentToken = currentTokenCreator(null)

        readSkipWhitespace()
    }

    private fun readFieldName() {
        readStringValue({ JsonToken.FieldName(this.storedValue) })
        if (lastChar != ':') {
            throwJsonException()
        }
        readSkipWhitespace()
    }

    private sealed class SkipCharType {
        object None : SkipCharType()
        object StartNewEscaped : SkipCharType()
        open class UtfChar(val charType: Char, private val charCount: Int) : SkipCharType() {
            protected var chars: CharArray = CharArray(charCount)
            private var index = 0
            fun addCharAndHasReachedEnd(char: Char): Boolean {
                chars[index++] = char
                if(index == charCount) {
                    return true
                }
                return false
            }
            open fun toCharString(): String {
                return chars.joinToString(separator = "").toInt(16).toChar().toString()
            }
            fun toOriginalChars(): String {
                return chars.sliceArray(0 until index).joinToString(separator = "")
            }
        }
    }

    private fun readStringValue(currentTokenCreator: (value: String?) -> JsonToken) {
        read()
        var skipChar: SkipCharType = SkipCharType.None
        loop@while(lastChar != '"' || skipChar == SkipCharType.StartNewEscaped) {
            fun addCharAndResetSkipChar(value: String): SkipCharType {
                storedValue += value
                return SkipCharType.None
            }

            skipChar = when (skipChar) {
                SkipCharType.None -> when(lastChar) {
                    '\\' -> SkipCharType.StartNewEscaped
                    else -> addCharAndResetSkipChar("$lastChar")
                }
                SkipCharType.StartNewEscaped -> when(lastChar) {
                    'b' -> addCharAndResetSkipChar("\b")
                    '"' -> addCharAndResetSkipChar("\"")
                    '\\' -> addCharAndResetSkipChar("\\")
                    '/' -> addCharAndResetSkipChar("/")
                    'f' -> addCharAndResetSkipChar("\u000C")
                    'n' -> addCharAndResetSkipChar("\n")
                    'r' -> addCharAndResetSkipChar("\r")
                    't' -> addCharAndResetSkipChar("\t")
                    'u' -> SkipCharType.UtfChar('u', 4)
                    else -> addCharAndResetSkipChar("\\$lastChar")
                }
                is SkipCharType.UtfChar -> when(lastChar.toLowerCase()) {
                    in HEX_CHARS -> {
                        if (skipChar.addCharAndHasReachedEnd(lastChar)) {
                            addCharAndResetSkipChar(skipChar.toCharString())
                        } else {
                            skipChar
                        }
                    }
                    else -> addCharAndResetSkipChar("\\${skipChar.charType}${skipChar.toOriginalChars()}$lastChar")
                }
            }
            read()
        }
        currentToken = currentTokenCreator(storedValue)

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
