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
import maryk.lib.extensions.isLineBreak
import maryk.lib.extensions.isLowerHexChar

private val skipArray = setOf(ObjectSeparator, ArraySeparator, StartDocument)

private enum class ReadMode {
    STRING,
}

private interface JsonCharReader {
    fun read(): Char
}

private class LambdaJsonCharReader(
    private val reader: () -> Char?
) : JsonCharReader {
    override fun read(): Char = reader() ?: throw ExceptionWhileReadingJson()
}

private class StringJsonCharReader(
    private val value: String
) : JsonCharReader {
    private var index = 0

    override fun read(): Char {
        if (index >= value.length) {
            throw ExceptionWhileReadingJson()
        }
        return value[index++]
    }
}

/** Describes JSON complex types */
internal enum class JsonComplexType {
    OBJECT, ARRAY
}

/** Reads JSON from the supplied [reader]. Return null to signal end of input. */
class JsonReader private constructor(
    private val reader: JsonCharReader
) : IsJsonLikeReader {
    constructor(reader: () -> Char?) : this(LambdaJsonCharReader(reader))

    constructor(json: String) : this(StringJsonCharReader(json))

    override var currentToken: JsonToken = StartDocument

    override var columnNumber = 0
    override var lineNumber = 1

    private var storedValue: String? = ""
    private val typeStack: MutableList<JsonComplexType> = mutableListOf()
    private var lastChar: Char = ' '
    private var resumedFromSuspension = false
    private var suspendedReadMode: ReadMode? = null
    private var suspendedStringSkipChar: SkipCharType = SkipCharType.None

    override fun nextToken(): JsonToken {
        if (resumedFromSuspension) {
            resumedFromSuspension = false
        } else {
            storedValue = ""
        }
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
                        typeStack.isEmpty() -> finishDocumentRead()
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
                    resumedFromSuspension = true
                    readSkipWhitespace()
                    if (suspendedReadMode == ReadMode.STRING) {
                        suspendedReadMode = null
                        when (currentToken) {
                            StartDocument, is FieldName, is StartArray, ArraySeparator -> {
                                readStringValue(this::constructJsonValueToken, skipInitialRead = true)
                            }
                            is StartObject, ObjectSeparator -> {
                                readFieldName(skipInitialRead = true)
                            }
                            else -> throwJsonException()
                        }
                        return if (currentToken in skipArray) nextToken() else currentToken
                    }
                    return nextToken()
                }
                is Stopped -> {
                    return currentToken
                }
                StartComplexFieldName, EndComplexFieldName -> {
                    throw JsonWriteException("Start and End ComplexFieldName not possible in JSON")
                }
            }
        } catch (_: ExceptionWhileReadingJson) {
            currentToken = Suspended(currentToken, storedValue)
        } catch (e: InvalidJsonContent) {
            currentToken = JsonException(e)
            e.columnNumber = this.columnNumber
            e.lineNumber = this.lineNumber
            throw e
        }

        return if (currentToken in skipArray) nextToken() else currentToken
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
            // Continue while there is not a field name on current stack depth or object has ended at below stack depth
            !((currentToken is FieldName && this.typeStack.count() <= startDepth) || (currentToken is EndObject && this.typeStack.count() < startDepth))
            && currentToken !is Stopped
        ) {
            handleSkipToken?.invoke(this.currentToken)
            nextToken()
        }
    }

    private fun read() = try {
        lastChar = reader.read()
        if (lastChar.isLineBreak()) {
            lineNumber += 1
            columnNumber = 0
        } else {
            columnNumber += 1
        }
    } catch (_: ExceptionWhileReadingJson) {
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
            typeStack.isEmpty() -> finishDocumentRead()
            else -> when (typeStack.last()) {
                OBJECT -> readObject()
                ARRAY -> readArray()
            }
        }
    }

    private fun finishDocumentRead() {
        try {
            read()
            skipWhiteSpace()
            throwJsonException()
        } catch (_: ExceptionWhileReadingJson) {
            currentToken = EndDocument
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

        // Number should contain at least one digit
        if (startedWithMinus && storedValue == "-") {
            throwJsonException()
        }

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

        currentToken = try {
            if (isExponent || isFraction) {
                val double = storedValue!!.toDouble()
                if (!double.isFinite()) throwJsonException()
                currentTokenCreator(double)
            } else {
                currentTokenCreator(storedValue!!.toLong())
            }
        } catch (_: NumberFormatException) {
            throwJsonException()
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

    private fun readFieldName(skipInitialRead: Boolean = false) {
        readStringValue({ FieldName(this.storedValue) }, skipInitialRead)
        if (lastChar != ':') {
            throwJsonException()
        }
        readSkipWhitespace()
    }

    private sealed class SkipCharType {
        object None : SkipCharType()
        object StartNewEscaped : SkipCharType()
        open class UtfChar(val charType: Char, private val charCount: Int) : SkipCharType() {
            private var chars: CharArray = CharArray(charCount)
            private var index = 0
            fun addCharAndHasReachedEnd(char: Char): Boolean {
                chars[index++] = char
                return index == charCount
            }

            open fun toCharString(): String {
                return chars.concatToString().toInt(16).toChar().toString()
            }

            fun toOriginalChars(): String {
                return chars.concatToString(0, index)
            }
        }
    }

    private fun readStringValue(
        currentTokenCreator: (value: String?) -> JsonToken,
        skipInitialRead: Boolean = false,
    ) {
        suspendedReadMode = ReadMode.STRING
        var skipChar = if (skipInitialRead) suspendedStringSkipChar else SkipCharType.None
        val valueBuilder = StringBuilder(storedValue.orEmpty())
        try {
            if (!skipInitialRead) read()
            loop@ while (lastChar != '"' || skipChar == SkipCharType.StartNewEscaped) {
                if (lastChar.isLineBreak()) {
                    throwJsonException()
                }
                if (skipChar == SkipCharType.None && lastChar < ' ') {
                    throwJsonException()
                }

                fun addCharAndResetSkipChar(value: String): SkipCharType {
                    valueBuilder.append(value)
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
                        else -> throwJsonException()
                    }
                    is SkipCharType.UtfChar -> if (lastChar.lowercaseChar().isLowerHexChar()) {
                        if (skipChar.addCharAndHasReachedEnd(lastChar)) {
                            addCharAndResetSkipChar(skipChar.toCharString())
                        } else {
                            skipChar
                        }
                    } else {
                        throwJsonException()
                    }
                }
                read()
            }
        } catch (error: ExceptionWhileReadingJson) {
            storedValue = valueBuilder.toString()
            suspendedStringSkipChar = skipChar
            throw error
        }
        storedValue = valueBuilder.toString()
        if (storedValue!!.hasUnpairedSurrogates()) {
            throwJsonException()
        }
        currentToken = currentTokenCreator(storedValue)
        storedValue = ""
        suspendedStringSkipChar = SkipCharType.None

        if (typeStack.isNotEmpty()) {
            readSkipWhitespace()
        }
        suspendedReadMode = null
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

    private fun throwJsonException(): Nothing {
        throw InvalidJsonContent("Invalid character '$lastChar' after $currentToken")
    }
}

private fun String.hasUnpairedSurrogates(): Boolean {
    var index = 0
    while (index < length) {
        val char = this[index]
        when {
            char.isHighSurrogate() -> {
                if (index + 1 >= length || !this[index + 1].isLowSurrogate()) {
                    return true
                }
                index++
            }
            char.isLowSurrogate() -> return true
        }
        index++
    }
    return false
}
