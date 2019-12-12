package maryk.json

import maryk.json.JsonComplexType.ARRAY
import maryk.json.JsonComplexType.OBJECT
import maryk.json.JsonToken.ArraySeparator
import maryk.json.JsonToken.EndArray
import maryk.json.JsonToken.EndComplexFieldName
import maryk.json.JsonToken.EndDocument
import maryk.json.JsonToken.EndObject
import maryk.json.JsonToken.FieldName
import maryk.json.JsonToken.JsonException
import maryk.json.JsonToken.NullValue
import maryk.json.JsonToken.ObjectSeparator
import maryk.json.JsonToken.SimpleStartArray
import maryk.json.JsonToken.SimpleStartObject
import maryk.json.JsonToken.StartArray
import maryk.json.JsonToken.StartComplexFieldName
import maryk.json.JsonToken.StartDocument
import maryk.json.JsonToken.StartObject
import maryk.json.JsonToken.Stopped
import maryk.json.JsonToken.Suspended
import maryk.json.JsonToken.Value
import maryk.lib.extensions.HEX_CHARS
import maryk.lib.extensions.isDigit
import maryk.lib.extensions.isLineBreak

private val skipArray = arrayOf(ObjectSeparator, ArraySeparator, StartDocument)

/** Describes JSON complex types */
internal enum class JsonComplexType {
    OBJECT, ARRAY
}

/** Reads JSON from the supplied [reader] */
class JsonReader(
    private val reader: () -> Char
) : IsJsonLikeReader {
    override var currentToken: JsonToken = StartDocument

    override var columnNumber = 0
    override var lineNumber = 1

    private var storedValue: String? = ""
    private val typeStack: MutableList<JsonComplexType> = mutableListOf()
    private var lastChar: Char = ' '

    override fun nextToken(): JsonToken {
        storedValue = ""
        try {
            when (currentToken) {
                StartDocument -> {
                    lastChar = readSkipWhitespace()
                    when (lastChar) {
                        '{' -> startObject()
                        '[' -> startArray()
                        '"' -> readStringValue(this::constructJsonValueToken)
                        else -> throwJsonException()
                    }
                }
                is StartObject -> {
                    typeStack.add(OBJECT)
                    when (lastChar) {
                        '}' -> endObject()
                        '"' -> readFieldName()
                        else -> throwJsonException()
                    }
                }
                EndObject -> {
                    continueComplexRead()
                }
                is StartArray -> {
                    typeStack.add(ARRAY)
                    if (lastChar == ']') {
                        endArray()
                    } else {
                        readValue(this::constructJsonValueToken)
                    }
                }
                EndArray -> {
                    continueComplexRead()
                }
                is FieldName -> {
                    readValue(this::constructJsonValueToken)
                }
                is Value<*> -> {
                    when {
                        typeStack.isEmpty() -> currentToken = EndDocument
                        typeStack.last() == OBJECT -> readObject()
                        else -> readArray()
                    }
                }
                ObjectSeparator -> {
                    when (lastChar) {
                        '"' -> readFieldName()
                        else -> throwJsonException()
                    }
                }
                ArraySeparator -> {
                    readValue(this::constructJsonValueToken)
                }
                is Suspended -> {
                    (currentToken as Suspended).let {
                        currentToken = it.lastToken
                        storedValue = it.storedValue
                    }
                    readSkipWhitespace()
                    return nextToken()
                }
                is Stopped -> {
                    return currentToken
                }
                StartComplexFieldName, EndComplexFieldName -> {
                    throw JsonWriteException("Start and End ComplexFieldName not possible in JSON")
                }
            }
        } catch (e: ExceptionWhileReadingJson) {
            currentToken = Suspended(currentToken, storedValue)
        } catch (e: InvalidJsonContent) {
            currentToken = JsonException(e)
            e.columnNumber = this.columnNumber
            e.lineNumber = this.lineNumber
            throw e
        }

        if (currentToken in skipArray) {
            return nextToken()
        }

        return currentToken
    }

    private fun constructJsonValueToken(it: Any?) =
        when (it) {
            null -> NullValue
            is Boolean -> Value(it, ValueType.Bool)
            is String -> Value(it, ValueType.String)
            is Double -> Value(it, ValueType.Float)
            is Long -> Value(it, ValueType.Int)
            else -> Value(it.toString(), ValueType.String)
        }

    override fun skipUntilNextField(handleSkipToken: ((JsonToken) -> Unit)?) {
        val startDepth = typeStack.count()
        nextToken()
        while (
            !((currentToken is FieldName || currentToken is EndObject) && this.typeStack.count() <= startDepth)
            && currentToken !is Stopped
        ) {
            handleSkipToken?.invoke(this.currentToken)
            nextToken()
        }
    }

    private fun read() = try {
        lastChar = reader()
        if (lastChar.isLineBreak()) {
            lineNumber += 1
            columnNumber = 0
        } else {
            columnNumber += 1
        }
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
            typeStack.isEmpty() -> currentToken = EndDocument
            else -> when (typeStack.last()) {
                OBJECT -> readObject()
                ARRAY -> readArray()
            }
        }
    }

    private fun readArray() {
        when (lastChar) {
            ',' -> {
                currentToken = ArraySeparator
                readSkipWhitespace()
            }
            ']' -> endArray()
            else -> throwJsonException()
        }
    }

    private fun readObject() {
        when (lastChar) {
            ',' -> {
                currentToken = ObjectSeparator
                readSkipWhitespace()
            }
            '}' -> endObject()
            else -> throwJsonException()
        }
    }

    private fun readValue(currentTokenCreator: (value: Any?) -> JsonToken) {
        when (this.lastChar) {
            '{' -> startObject()
            '[' -> startArray()
            '"' -> readStringValue(currentTokenCreator)
            '-' -> readNumber(true, currentTokenCreator)
            'n' -> readNullValue(currentTokenCreator)
            't' -> readTrue(currentTokenCreator)
            'f' -> readFalse(currentTokenCreator)
            else -> {
                if (this.lastChar.isDigit()) {
                    readNumber(false, currentTokenCreator)
                } else {
                    throwJsonException()
                }
            }
        }
    }

    private fun readNumber(startedWithMinus: Boolean, currentTokenCreator: (value: Any?) -> JsonToken) {
        fun addAndAdvance() {
            storedValue += lastChar
            read()
        }

        // Read number
        do {
            addAndAdvance()
        } while (lastChar.isDigit())

        // Check if value starts with illegal 0
        storedValue?.let {
            if (startedWithMinus && it.length > 2 && it[1] == '0') {
                throwJsonException()
            } else if (it.length > 1 && it[0] == '0') {
                throwJsonException()
            }
        }

        // Read fraction
        val isFraction = if (lastChar == '.') {
            addAndAdvance()
            if (!lastChar.isDigit()) throwJsonException()
            do {
                addAndAdvance()
            } while (lastChar.isDigit())
            true
        } else {
            false
        }

        // read exponent
        val isExponent = if (lastChar in arrayOf('e', 'E')) {
            addAndAdvance()
            if (lastChar in arrayOf('+', '-')) {
                addAndAdvance()
            }
            if (!lastChar.isDigit()) throwJsonException()
            do {
                addAndAdvance()
            } while (lastChar.isDigit())
            true
        } else {
            false
        }

        currentToken = if (isExponent || isFraction) {
            currentTokenCreator(storedValue!!.toDouble())
        } else {
            currentTokenCreator(storedValue!!.toLong())
        }

        skipWhiteSpace()
    }

    private fun readFalse(currentTokenCreator: (value: Any?) -> JsonToken) {
        for (it in "alse") {
            read()
            if (lastChar != it) {
                throwJsonException()
            }
        }
        currentToken = currentTokenCreator(false)

        readSkipWhitespace()
    }

    private fun readTrue(currentTokenCreator: (value: Any?) -> JsonToken) {
        ("rue").forEach {
            read()
            if (lastChar != it) {
                throwJsonException()
            }
        }
        currentToken = currentTokenCreator(true)

        readSkipWhitespace()
    }

    private fun readNullValue(currentTokenCreator: (value: String?) -> JsonToken) {
        for (it in "ull") {
            read()
            if (lastChar != it) {
                throwJsonException()
            }
        }
        storedValue = null

        currentToken = currentTokenCreator(null)

        readSkipWhitespace()
    }

    private fun readFieldName() {
        readStringValue { FieldName(this.storedValue) }
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
                if (index == charCount) {
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
        loop@ while (lastChar != '"' || skipChar == SkipCharType.StartNewEscaped) {
            fun addCharAndResetSkipChar(value: String): SkipCharType {
                storedValue += value
                return SkipCharType.None
            }

            skipChar = when (skipChar) {
                SkipCharType.None -> when (lastChar) {
                    '\\' -> SkipCharType.StartNewEscaped
                    else -> addCharAndResetSkipChar("$lastChar")
                }
                SkipCharType.StartNewEscaped -> when (lastChar) {
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
                is SkipCharType.UtfChar -> when (lastChar.toLowerCase()) {
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

        if (typeStack.isNotEmpty()) {
            readSkipWhitespace()
        }
    }

    private fun startObject() {
        currentToken = SimpleStartObject
        readSkipWhitespace()
    }

    private fun endObject() {
        typeStack.removeAt(typeStack.lastIndex)
        currentToken = EndObject
        if (typeStack.isNotEmpty()) {
            readSkipWhitespace()
        }
    }

    private fun startArray() {
        currentToken = SimpleStartArray
        readSkipWhitespace()
    }

    private fun endArray() {
        typeStack.removeAt(typeStack.lastIndex)
        currentToken = EndArray
        if (typeStack.isNotEmpty()) {
            readSkipWhitespace()
        }
    }

    private fun throwJsonException() {
        throw InvalidJsonContent("Invalid character '$lastChar' after $currentToken")
    }
}
