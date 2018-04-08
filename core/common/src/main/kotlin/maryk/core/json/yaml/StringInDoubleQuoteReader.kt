package maryk.core.json.yaml

import maryk.core.bytes.fromCodePoint
import maryk.core.extensions.HEX_CHARS
import maryk.core.json.ExceptionWhileReadingJson
import maryk.core.json.JsonToken
import maryk.core.json.TokenType

/**
 * Reads a double quote string
 * Pass [tag] to set type on Value.
 * [jsonTokenCreator] creates the right jsonToken. Could be field name or value.
 */
internal fun YamlCharReader.doubleQuoteString(tag: TokenType?, indentAtStart: Int, jsonTokenCreator: JsonTokenCreator): JsonToken {
    var foundValue: String? = ""

    fun addCharAndResetSkipChar(value: String): SkipCharType {
        foundValue += value
        return SkipCharType.None
    }

    try {
        read()

        var skipChar: SkipCharType = SkipCharType.None
        loop@while(lastChar != '"' || skipChar == SkipCharType.StartNewEscaped) {
            skipChar = when (skipChar) {
                SkipCharType.None -> when(lastChar) {
                    '\\' -> SkipCharType.StartNewEscaped
                    else -> addCharAndResetSkipChar("$lastChar")
                }
                SkipCharType.StartNewEscaped -> when(lastChar) {
                    '0' -> addCharAndResetSkipChar("\u0000")
                    'a' -> addCharAndResetSkipChar("\u0007")
                    'b' -> addCharAndResetSkipChar("\b")
                    't', '\t' -> addCharAndResetSkipChar("\t")
                    'n' -> addCharAndResetSkipChar("\n")
                    'v' -> addCharAndResetSkipChar("\u000B")
                    'f' -> addCharAndResetSkipChar("\u000C")
                    'r' -> addCharAndResetSkipChar("\r")
                    'e' -> addCharAndResetSkipChar("\u001B")
                    ' ' -> addCharAndResetSkipChar(" ")
                    '"' -> addCharAndResetSkipChar("\"")
                    '/' -> addCharAndResetSkipChar("/")
                    '\\' -> addCharAndResetSkipChar("\\")
                    'N' -> addCharAndResetSkipChar("\u0085")
                    '_' -> addCharAndResetSkipChar("\u00A0")
                    'L' -> addCharAndResetSkipChar("\u2028")
                    'P' -> addCharAndResetSkipChar("\u2029")
                    'x' -> SkipCharType.UtfChar('x', 2)
                    'u' -> SkipCharType.UtfChar('u', 4)
                    'U' -> SkipCharType.Utf32Char()
                    '\n', '\r' -> SkipCharType.None
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

        try {
            read()  // Skip last double quote and if fails just continue since tag is complete
        } catch (e: ExceptionWhileReadingJson) {
            this.yamlReader.hasException = true
        }

        return jsonTokenCreator(foundValue, false, tag, indentAtStart)
    } catch (e: ExceptionWhileReadingJson) {
        throw InvalidYamlContent("Double quoted string was never closed")
    }
}


/** Defines type of Char skipping mode */
private sealed class SkipCharType {
    /** No Char skipping */
    object None : SkipCharType()

    /** New unknown skip type found */
    object StartNewEscaped : SkipCharType()

    /** UTF char skip found */
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

    /** UTF 32 char skip found */
    class Utf32Char : UtfChar(charType = 'U', charCount = 8) {
        override fun toCharString(): String {
            return fromCodePoint(chars.joinToString(separator = "").toInt(16))
        }
    }
}
