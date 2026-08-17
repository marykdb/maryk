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

private enum class StringReadMode {
    VALUE,
    FIELD_NAME,
}

private enum class NumberReadStage {
    INTEGER_REQUIRED,
    INTEGER_ZERO,
    INTEGER,
    FRACTION_REQUIRED,
    FRACTION,
    EXPONENT_SIGN_OR_DIGIT,
    EXPONENT_REQUIRED,
    EXPONENT,
}

private enum class WhitespaceReadResume {
    RETURN_CURRENT,
    NEXT_TOKEN,
    READ_DOCUMENT,
    FINISH_FIELD_NAME,
    NUMBER_TRAILING,
    FINISH_DOCUMENT,
    ROOT_SCALAR,
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

/** Maximum number of open JSON objects and arrays on one path. */
private const val MAX_JSON_STRUCTURE_DEPTH = 128

/** Persistent stack node so a resumable reader state can capture nesting in constant time. */
private class JsonTypeStack(
    val type: JsonComplexType,
    val previous: JsonTypeStack?,
    val depth: Int
)

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
    private var typeStack: JsonTypeStack? = null
    private var lastChar: Char = ' '
    private var resumedFromSuspension = false
    private var readReplay: String? = null
    private var readReplayIndex = 0
    private var readAttempt: StringBuilder? = null
    private var suspendedState: ReaderState? = null
    private var suspendedReadReplay: String? = null
    private var suspendedAfterToken: ReaderState? = null
    private var suspendedStringRead: StringReadState? = null
    private var suspendedNumberRead: NumberReadState? = null
    private var suspendedFieldName: FieldName? = null
    private var suspendedFieldNamePreviousToken: JsonToken? = null
    private var suspendedWhitespaceRead: WhitespaceReadState? = null
    private var fieldNameColonRead = false

    private data class ReaderState(
        val currentToken: JsonToken,
        val storedValue: String?,
        val typeStack: JsonTypeStack?,
        val lastChar: Char,
        val columnNumber: Int,
        val lineNumber: Int,
        val fieldNameColonRead: Boolean,
    )

    private data class StringReadState(
        val mode: StringReadMode,
        val value: StringBuilder,
        var skipChar: SkipCharType = SkipCharType.None,
    )

    private class NumberReadState {
        val value = StringBuilder()
        var stage = NumberReadStage.INTEGER
        var isFloatingPoint = false

        fun canFinish() = stage in setOf(
            NumberReadStage.INTEGER_ZERO,
            NumberReadStage.INTEGER,
            NumberReadStage.FRACTION,
            NumberReadStage.EXPONENT,
        )
    }

    private class WhitespaceReadState(
        val resume: WhitespaceReadResume,
        var needsRead: Boolean,
        var continuationToken: JsonToken? = null,
        var lastToken: JsonToken? = null,
    )

    override fun nextToken(): JsonToken {
        if (resumedFromSuspension) {
            resumedFromSuspension = false
        } else {
            storedValue = ""
        }
        val state = captureState()
        val previousReadAttempt = readAttempt
        readAttempt = StringBuilder()
        try {
            when (currentToken) {
                StartDocument -> {
                    lastChar = readSkipWhitespace(WhitespaceReadResume.READ_DOCUMENT)
                    readDocumentValue()
                }
                is StartObject -> {
                    pushType(OBJECT)
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
                    pushType(ARRAY)
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
                        typeStack == null -> finishDocumentRead()
                        typeStack?.type == OBJECT -> readObject()
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
                    val whitespaceRead = suspendedWhitespaceRead
                    if (whitespaceRead != null) {
                        currentToken = whitespaceRead.continuationToken ?: (currentToken as Suspended).lastToken
                        resumedFromSuspension = true
                        continueWhitespaceRead()
                        return resumeAfterWhitespace(whitespaceRead.resume)
                    }
                    if (suspendedStringRead != null) {
                        currentToken = (currentToken as Suspended).lastToken
                        resumedFromSuspension = true
                        read()
                        continueStringRead()
                        return if (currentToken in skipArray) nextToken() else currentToken
                    }
                    if (suspendedNumberRead != null) {
                        currentToken = (currentToken as Suspended).lastToken
                        resumedFromSuspension = true
                        continueNumberRead()
                        return if (currentToken in skipArray) nextToken() else currentToken
                    }
                    if (suspendedFieldName != null) {
                        currentToken = suspendedFieldName!!
                        resumedFromSuspension = true
                        continueFieldName()
                        return if (currentToken in skipArray) nextToken() else currentToken
                    }
                    val suspendedAfterToken = this.suspendedAfterToken
                    if (suspendedAfterToken != null) {
                        this.suspendedAfterToken = null
                        restore(suspendedAfterToken)
                        try {
                            readSkipWhitespace(WhitespaceReadResume.NEXT_TOKEN)
                        } catch (_: ExceptionWhileReadingJson) {
                            suspendedWhitespaceRead?.continuationToken = currentToken
                            this.suspendedAfterToken = captureState()
                            currentToken = Suspended(currentToken, storedValue)
                            return currentToken
                        }
                        return nextToken()
                    }
                    val suspendedState = this.suspendedState ?: return currentToken
                    restore(suspendedState)
                    this.suspendedState = null
                    readReplay = suspendedReadReplay
                    readReplayIndex = 0
                    suspendedReadReplay = null
                    resumedFromSuspension = true
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
            val partialValue = storedValue
            val stringRead = suspendedStringRead
            val numberRead = suspendedNumberRead
            val whitespaceRead = suspendedWhitespaceRead
            if (whitespaceRead != null) {
                whitespaceRead.continuationToken = currentToken
                val lastToken = whitespaceRead.lastToken ?: if (
                    whitespaceRead.resume == WhitespaceReadResume.NEXT_TOKEN ||
                    whitespaceRead.resume == WhitespaceReadResume.ROOT_SCALAR ||
                    whitespaceRead.resume == WhitespaceReadResume.NUMBER_TRAILING
                ) {
                    currentToken
                } else {
                    lastResumableToken(state.currentToken)
                }
                whitespaceRead.lastToken = lastToken
                currentToken = Suspended(lastToken, partialValue)
            } else if (stringRead != null) {
                // Preserve the public suspension snapshot while retaining the mutable buffer for resumption.
                currentToken = Suspended(currentToken, stringRead.value.toString())
            } else if (numberRead != null) {
                // Preserve the public suspension snapshot while retaining the mutable buffer for resumption.
                currentToken = Suspended(currentToken, numberRead.value.toString())
            } else if (currentToken is FieldName && !fieldNameColonRead) {
                suspendedFieldName = currentToken as FieldName
                currentToken = Suspended(suspendedFieldNamePreviousToken ?: state.currentToken, partialValue)
            } else if (
                currentToken !== state.currentToken &&
                (currentToken is Value<*> || (currentToken is FieldName && fieldNameColonRead))
            ) {
                suspendedAfterToken = captureState()
                suspendedState = null
                suspendedReadReplay = null
                readReplay = null
                readReplayIndex = 0
                currentToken = Suspended(suspendedAfterToken!!.currentToken, partialValue)
            } else {
                val remainingReplay = readReplay?.let { it.substring(readReplayIndex) }.orEmpty()
                restore(state)
                suspendedState = state
                suspendedReadReplay = readAttempt?.toString() + remainingReplay
                readReplay = null
                readReplayIndex = 0
                currentToken = Suspended(state.currentToken, partialValue)
            }
        } catch (e: InvalidJsonContent) {
            currentToken = JsonException(e)
            e.columnNumber = this.columnNumber
            e.lineNumber = this.lineNumber
            throw e
        } finally {
            readAttempt = previousReadAttempt
        }

        return if (currentToken in skipArray) nextToken() else currentToken
    }

    private fun restore(state: ReaderState) {
        currentToken = state.currentToken
        storedValue = state.storedValue
        typeStack = state.typeStack
        lastChar = state.lastChar
        columnNumber = state.columnNumber
        lineNumber = state.lineNumber
        fieldNameColonRead = state.fieldNameColonRead
    }

    private fun lastResumableToken(token: JsonToken): JsonToken = when (token) {
        is Suspended -> lastResumableToken(token.lastToken)
        else -> token
    }

    private fun captureState() = ReaderState(
        currentToken = currentToken,
        storedValue = storedValue,
        typeStack = typeStack,
        lastChar = lastChar,
        columnNumber = columnNumber,
        lineNumber = lineNumber,
        fieldNameColonRead = fieldNameColonRead,
    )

    private fun constructJsonValueToken(it: Any?) =
        when (it) {
            null -> NullValue
            is Boolean -> Value(it, ValueType.Bool)
            is String -> Value(it, ValueType.String)
            is Double -> Value(it, ValueType.Float)
            is Long -> Value(it, ValueType.Int)
            else -> Value(it.toString(), ValueType.String)
        }

    private fun readDocumentValue() {
        when (lastChar) {
            '{' -> startObject()
            '[' -> startArray()
            '"' -> readStringValue(StringReadMode.VALUE)
            else -> readValue(this::constructJsonValueToken)
        }
    }

    override fun skipUntilNextField(handleSkipToken: ((JsonToken) -> Unit)?) {
        val startDepth = typeStack?.depth ?: 0
        nextToken()
        while (
            // Continue while there is not a field name on current stack depth or object has ended at below stack depth
            !((currentToken is FieldName && (this.typeStack?.depth ?: 0) <= startDepth) || (currentToken is EndObject && (this.typeStack?.depth ?: 0) < startDepth))
            && currentToken !is Stopped
        ) {
            handleSkipToken?.invoke(this.currentToken)
            nextToken()
        }
    }

    private fun read() = try {
        val replay = readReplay
        lastChar = if (replay != null && readReplayIndex < replay.length) {
            replay[readReplayIndex++].also {
                if (readReplayIndex == replay.length) {
                    readReplay = null
                    readReplayIndex = 0
                }
            }
        } else {
            reader.read()
        }
        readAttempt?.append(lastChar)
        if (lastChar.isLineBreak()) {
            lineNumber += 1
            columnNumber = 0
        } else {
            columnNumber += 1
        }
    } catch (_: ExceptionWhileReadingJson) {
        throw ExceptionWhileReadingJson()
    }

    private fun readSkipWhitespace(resume: WhitespaceReadResume = WhitespaceReadResume.RETURN_CURRENT): Char {
        suspendedWhitespaceRead = WhitespaceReadState(resume, needsRead = true)
        continueWhitespaceRead()
        return lastChar
    }

    private fun skipWhiteSpace(resume: WhitespaceReadResume = WhitespaceReadResume.RETURN_CURRENT) {
        if (lastChar.isJsonWhitespace()) {
            suspendedWhitespaceRead = WhitespaceReadState(resume, needsRead = false)
            continueWhitespaceRead()
        }
    }

    private fun continueWhitespaceRead() {
        val whitespaceRead = suspendedWhitespaceRead ?: return
        if (whitespaceRead.needsRead) {
            read()
            whitespaceRead.needsRead = false
        }
        while (lastChar.isJsonWhitespace()) {
            read()
        }
        suspendedWhitespaceRead = null
    }

    private fun resumeAfterWhitespace(resume: WhitespaceReadResume): JsonToken = when (resume) {
        WhitespaceReadResume.RETURN_CURRENT -> currentToken
        WhitespaceReadResume.NEXT_TOKEN -> nextToken()
        WhitespaceReadResume.READ_DOCUMENT -> {
            readDocumentValue()
            if (currentToken in skipArray) nextToken() else currentToken
        }
        WhitespaceReadResume.FINISH_FIELD_NAME -> {
            finishFieldName()
            currentToken
        }
        WhitespaceReadResume.NUMBER_TRAILING -> {
            validateNumberTrailingCharacter()
            nextToken()
        }
        WhitespaceReadResume.FINISH_DOCUMENT -> throwJsonException()
        WhitespaceReadResume.ROOT_SCALAR -> {
            if (typeStack == null) throwJsonException()
            nextToken()
        }
    }

    private fun continueComplexRead() {
        when {
            typeStack == null -> finishDocumentRead()
            else -> when (typeStack!!.type) {
                OBJECT -> readObject()
                ARRAY -> readArray()
            }
        }
    }

    private fun finishDocumentRead() {
        try {
            readSkipWhitespace(WhitespaceReadResume.FINISH_DOCUMENT)
            throwJsonException()
        } catch (_: ExceptionWhileReadingJson) {
            suspendedWhitespaceRead = null
            currentToken = EndDocument
        }
    }

    private fun readArray() {
        when (lastChar) {
            ',' -> {
                currentToken = ArraySeparator
                readSkipWhitespace(WhitespaceReadResume.NEXT_TOKEN)
            }
            ']' -> endArray()
            else -> throwJsonException()
        }
    }

    private fun readObject() {
        when (lastChar) {
            ',' -> {
                currentToken = ObjectSeparator
                readSkipWhitespace(WhitespaceReadResume.NEXT_TOKEN)
            }
            '}' -> endObject()
            else -> throwJsonException()
        }
    }

    private fun readValue(currentTokenCreator: (value: Any?) -> JsonToken) {
        when (this.lastChar) {
            '{' -> startObject()
            '[' -> startArray()
            '"' -> readStringValue(StringReadMode.VALUE)
            '-' -> readNumber(true)
            'n' -> readNullValue(currentTokenCreator)
            't' -> readTrue(currentTokenCreator)
            'f' -> readFalse(currentTokenCreator)
            else -> {
                if (this.lastChar.isDigit()) {
                    readNumber(false)
                } else {
                    throwJsonException()
                }
            }
        }
    }

    private fun readNumber(startedWithMinus: Boolean) {
        suspendedNumberRead = NumberReadState().also { numberRead ->
            when {
                startedWithMinus -> {
                    numberRead.value.append('-')
                    numberRead.stage = NumberReadStage.INTEGER_REQUIRED
                }
                lastChar == '0' -> {
                    numberRead.value.append(lastChar)
                    numberRead.stage = NumberReadStage.INTEGER_ZERO
                }
                else -> numberRead.value.append(lastChar)
            }
        }
        continueNumberRead()
    }

    private fun continueNumberRead() {
        val numberRead = suspendedNumberRead ?: return
        while (true) {
            try {
                read()
            } catch (error: ExceptionWhileReadingJson) {
                if (typeStack == null && numberRead.canFinish()) {
                    finishNumber(reachedDefinitiveRootEnd = true)
                    return
                }
                if (typeStack == null) {
                    throwJsonException()
                }
                throw error
            }

            when (numberRead.stage) {
                NumberReadStage.INTEGER_REQUIRED -> when {
                    lastChar == '0' -> {
                        numberRead.value.append(lastChar)
                        numberRead.stage = NumberReadStage.INTEGER_ZERO
                    }
                    lastChar.isDigit() -> {
                        numberRead.value.append(lastChar)
                        numberRead.stage = NumberReadStage.INTEGER
                    }
                    else -> throwJsonException()
                }
                NumberReadStage.INTEGER_ZERO -> when (lastChar) {
                    '.' -> startFraction(numberRead)
                    'e', 'E' -> startExponent(numberRead)
                    else -> if (lastChar.isDigit()) throwJsonException() else finishNumber()
                }
                NumberReadStage.INTEGER -> when (lastChar) {
                    '.' -> startFraction(numberRead)
                    'e', 'E' -> startExponent(numberRead)
                    else -> if (lastChar.isDigit()) numberRead.value.append(lastChar) else finishNumber()
                }
                NumberReadStage.FRACTION_REQUIRED -> {
                    if (!lastChar.isDigit()) throwJsonException()
                    numberRead.value.append(lastChar)
                    numberRead.stage = NumberReadStage.FRACTION
                }
                NumberReadStage.FRACTION -> when (lastChar) {
                    'e', 'E' -> startExponent(numberRead)
                    else -> if (lastChar.isDigit()) numberRead.value.append(lastChar) else finishNumber()
                }
                NumberReadStage.EXPONENT_SIGN_OR_DIGIT -> when {
                    lastChar in arrayOf('+', '-') -> {
                        numberRead.value.append(lastChar)
                        numberRead.stage = NumberReadStage.EXPONENT_REQUIRED
                    }
                    lastChar.isDigit() -> {
                        numberRead.value.append(lastChar)
                        numberRead.stage = NumberReadStage.EXPONENT
                    }
                    else -> throwJsonException()
                }
                NumberReadStage.EXPONENT_REQUIRED -> {
                    if (!lastChar.isDigit()) throwJsonException()
                    numberRead.value.append(lastChar)
                    numberRead.stage = NumberReadStage.EXPONENT
                }
                NumberReadStage.EXPONENT -> {
                    if (lastChar.isDigit()) numberRead.value.append(lastChar) else finishNumber()
                }
            }

            if (suspendedNumberRead == null) return
        }
    }

    private fun startFraction(numberRead: NumberReadState) {
        numberRead.value.append(lastChar)
        numberRead.stage = NumberReadStage.FRACTION_REQUIRED
        numberRead.isFloatingPoint = true
    }

    private fun startExponent(numberRead: NumberReadState) {
        numberRead.value.append(lastChar)
        numberRead.stage = NumberReadStage.EXPONENT_SIGN_OR_DIGIT
        numberRead.isFloatingPoint = true
    }

    private fun finishNumber(reachedDefinitiveRootEnd: Boolean = false) {
        val numberRead = suspendedNumberRead ?: return
        val value = numberRead.value.toString()
        currentToken = try {
            if (numberRead.isFloatingPoint) {
                val double = value.toDouble()
                if (!double.isFinite()) throwJsonException()
                constructJsonValueToken(double)
            } else {
                constructJsonValueToken(value.toLong())
            }
        } catch (_: NumberFormatException) {
            throwJsonException()
        }
        suspendedNumberRead = null

        if (!reachedDefinitiveRootEnd) {
            try {
                skipWhiteSpace(WhitespaceReadResume.NUMBER_TRAILING)
            } catch (error: ExceptionWhileReadingJson) {
                if (typeStack != null) {
                    throw error
                }
                suspendedWhitespaceRead = null
            }
            validateNumberTrailingCharacter()
        }
    }

    private fun validateNumberTrailingCharacter() {
        if (typeStack == null && !lastChar.isJsonWhitespace()) {
            throwJsonException()
        }
    }

    private fun readFalse(currentTokenCreator: (value: Any?) -> JsonToken) {
        for (it in "alse") {
            readKeywordCharacter()
            if (lastChar != it) {
                throwJsonException()
            }
        }
        currentToken = currentTokenCreator(false)

        readAfterRootScalar()
    }

    private fun readTrue(currentTokenCreator: (value: Any?) -> JsonToken) {
        ("rue").forEach {
            readKeywordCharacter()
            if (lastChar != it) {
                throwJsonException()
            }
        }
        currentToken = currentTokenCreator(true)

        readAfterRootScalar()
    }

    private fun readNullValue(currentTokenCreator: (value: String?) -> JsonToken) {
        for (it in "ull") {
            readKeywordCharacter()
            if (lastChar != it) {
                throwJsonException()
            }
        }
        storedValue = null

        currentToken = currentTokenCreator(null)

        readAfterRootScalar()
    }

    private fun readAfterRootScalar() {
        try {
            readSkipWhitespace(WhitespaceReadResume.ROOT_SCALAR)
        } catch (error: ExceptionWhileReadingJson) {
            if (typeStack == null) {
                suspendedWhitespaceRead = null
                return
            }
            throw error
        }
        if (typeStack == null) {
            throwJsonException()
        }
    }

    private fun readKeywordCharacter() {
        try {
            read()
        } catch (error: ExceptionWhileReadingJson) {
            if (typeStack == null) {
                throwJsonException()
            }
            throw error
        }
    }

    private fun readFieldName() {
        fieldNameColonRead = false
        suspendedFieldNamePreviousToken = currentToken
        readStringValue(StringReadMode.FIELD_NAME)
    }

    private fun finishFieldName() {
        if (lastChar != ':') {
            throwJsonException()
        }
        fieldNameColonRead = true
        suspendedFieldName = null
        suspendedFieldNamePreviousToken = null
        readSkipWhitespace(WhitespaceReadResume.NEXT_TOKEN)
    }

    private fun continueFieldName() {
        readSkipWhitespace(WhitespaceReadResume.FINISH_FIELD_NAME)
        finishFieldName()
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
        mode: StringReadMode,
    ) {
        suspendedStringRead = StringReadState(mode, StringBuilder(storedValue.orEmpty()))
        read()
        continueStringRead()
    }

    private fun continueStringRead() {
        val stringRead = suspendedStringRead ?: return
        while (lastChar != '"' || stringRead.skipChar == SkipCharType.StartNewEscaped) {
            if (lastChar.isLineBreak()) {
                throwJsonException()
            }
            val skipChar = stringRead.skipChar
            if (skipChar == SkipCharType.None && lastChar < ' ') {
                throwJsonException()
            }

            fun addCharAndResetSkipChar(value: String): SkipCharType {
                stringRead.value.append(value)
                return SkipCharType.None
            }

            stringRead.skipChar = when (skipChar) {
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
        storedValue = stringRead.value.toString()
        if (storedValue!!.hasUnpairedSurrogates()) {
            throwJsonException()
        }
        currentToken = when (stringRead.mode) {
            StringReadMode.VALUE -> constructJsonValueToken(storedValue)
            StringReadMode.FIELD_NAME -> FieldName(storedValue)
        }
        storedValue = ""
        suspendedStringRead = null
        if (typeStack != null) {
            readSkipWhitespace(
                if (stringRead.mode == StringReadMode.FIELD_NAME) {
                    WhitespaceReadResume.FINISH_FIELD_NAME
                } else {
                    WhitespaceReadResume.NEXT_TOKEN
                }
            )
        }
        if (stringRead.mode == StringReadMode.FIELD_NAME) {
            finishFieldName()
        }
    }

    private fun startObject() {
        currentToken = SimpleStartObject
        readSkipWhitespace(WhitespaceReadResume.RETURN_CURRENT)
    }

    private fun endObject() {
        popType()
        currentToken = EndObject
        if (typeStack != null) {
            readSkipWhitespace(WhitespaceReadResume.RETURN_CURRENT)
        }
    }

    private fun startArray() {
        currentToken = SimpleStartArray
        readSkipWhitespace(WhitespaceReadResume.RETURN_CURRENT)
    }

    private fun endArray() {
        popType()
        currentToken = EndArray
        if (typeStack != null) {
            readSkipWhitespace(WhitespaceReadResume.RETURN_CURRENT)
        }
    }

    private fun throwJsonException(): Nothing {
        throw InvalidJsonContent("Invalid character '$lastChar' after $currentToken")
    }

    private fun pushType(type: JsonComplexType) {
        val depth = (typeStack?.depth ?: 0) + 1
        if (depth > MAX_JSON_STRUCTURE_DEPTH) {
            throw InvalidJsonContent("JSON structure nesting exceeds $MAX_JSON_STRUCTURE_DEPTH")
        }
        typeStack = JsonTypeStack(type, typeStack, depth)
    }

    private fun popType() {
        val currentType = typeStack ?: throwJsonException()
        typeStack = currentType.previous
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

private fun Char.isJsonWhitespace() = this == ' ' || this == '\t' || this == '\n' || this == '\r'
