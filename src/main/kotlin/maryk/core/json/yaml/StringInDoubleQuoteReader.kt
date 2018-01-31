package maryk.core.json.yaml

import maryk.core.extensions.HEX_CHARS
import maryk.core.json.InvalidJsonContent
import maryk.core.json.JsonToken

private sealed class SkipCharType {
    object NONE: SkipCharType()
    object START_NEW: SkipCharType()
    object NEW_UTF_CHAR: SkipCharType()
    class UTF_CHAR1(val c1: Char): SkipCharType()
    class UTF_CHAR2(val c1: Char, val c2: Char): SkipCharType()
    class UTF_CHAR3(val c1: Char, val c2: Char, val c3: Char): SkipCharType()
}

/** Reads Strings encoded with "double quotes" */
internal class StringInDoubleQuoteReader(
    yamlReader: YamlReader,
    parentReader: YamlCharReader,
    private val jsonTokenConstructor: (String?) -> JsonToken
) : YamlCharReader(yamlReader, parentReader) {
    private var storedValue: String? = ""

    override fun readUntilToken(): JsonToken {
        read()
        var skipChar: SkipCharType = SkipCharType.NONE
        loop@while(lastChar != '"' || skipChar == SkipCharType.START_NEW) {
            when (skipChar) {
                SkipCharType.NONE -> when(lastChar) {
                    '\\' -> { skipChar = SkipCharType.START_NEW }
                    else -> storedValue += lastChar
                }
                SkipCharType.START_NEW -> when(lastChar) {
                    'b' -> { storedValue += '\b'; skipChar = SkipCharType.NONE }
                    '"' -> { storedValue += '"'; skipChar = SkipCharType.NONE }
                    '\\' -> { storedValue += '\\'; skipChar = SkipCharType.NONE }
                    '/' -> { storedValue += '/'; skipChar = SkipCharType.NONE }
                    'f' -> { storedValue += '\u000C'; skipChar = SkipCharType.NONE }
                    'n' -> { storedValue += '\n'; skipChar = SkipCharType.NONE }
                    'r' -> { storedValue += '\r'; skipChar = SkipCharType.NONE }
                    't' -> { storedValue += '\t'; skipChar = SkipCharType.NONE }
                    'u' -> { skipChar = SkipCharType.NEW_UTF_CHAR }
                }
                SkipCharType.NEW_UTF_CHAR -> when(lastChar.toLowerCase()) {
                    in HEX_CHARS -> skipChar = SkipCharType.UTF_CHAR1(lastChar)
                    else -> {
                        storedValue += "\\u" + lastChar
                        skipChar = SkipCharType.NONE
                    };
                }
                is SkipCharType.UTF_CHAR1 -> when(lastChar.toLowerCase()) {
                    in HEX_CHARS -> skipChar = SkipCharType.UTF_CHAR2(skipChar.c1, lastChar)
                    else -> {
                        storedValue += "\\u" + skipChar.c1 + lastChar
                        skipChar = SkipCharType.NONE
                    }
                }
                is SkipCharType.UTF_CHAR2 -> when(lastChar.toLowerCase()) {
                    in HEX_CHARS -> skipChar = SkipCharType.UTF_CHAR3(skipChar.c1, skipChar.c2, lastChar)
                    else -> {
                        storedValue += "\\u" + skipChar.c1 + skipChar.c2 + lastChar
                        skipChar = SkipCharType.NONE
                    }
                }
                is SkipCharType.UTF_CHAR3 -> when(lastChar.toLowerCase()) {
                    in HEX_CHARS -> {
                        storedValue += "${skipChar.c1}${skipChar.c2}${skipChar.c3}$lastChar".toInt(16).toChar()
                        skipChar = SkipCharType.NONE
                    }
                    else -> {
                        storedValue += "\\u" + skipChar.c1 + skipChar.c2 + skipChar.c3 + lastChar
                        skipChar = SkipCharType.NONE
                    };
                }
            }
            read()
        }

        currentReader = this.parentReader!!

        return this.jsonTokenConstructor(storedValue)
    }

    override fun handleReaderInterrupt(): JsonToken {
        throw InvalidJsonContent("Double quoted string was never closed")
    }
}