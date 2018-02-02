package maryk.core.json.yaml

import maryk.core.bytes.fromCodePoint
import maryk.core.extensions.HEX_CHARS
import maryk.core.json.InvalidJsonContent
import maryk.core.json.JsonToken

private sealed class SkipCharType {
    object NONE: SkipCharType()
    object START_NEW: SkipCharType()
    open class NEW_UTF_CHAR(val charType: Char, private val charCount: Int) : SkipCharType() {
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
    class NEW_UTF32_CHAR() : NEW_UTF_CHAR(charType = 'U', charCount = 8) {
        override fun toCharString(): String {
            println(chars.joinToString(separator = "").toInt(16))
            return fromCodePoint(chars.joinToString(separator = "").toInt(16))
        }
    }
}

/** Reads Strings encoded with "double quotes" */
internal class StringInDoubleQuoteReader(
    yamlReader: YamlReader,
    parentReader: YamlCharReader,
    private val jsonTokenConstructor: (String?) -> JsonToken
) : YamlCharReader(yamlReader, parentReader) {
    private var storedValue: String? = ""

    private fun addCharAndResetSkipChar(value: String): SkipCharType {
        storedValue += value
        return SkipCharType.NONE
    }

    override fun readUntilToken(): JsonToken {
        read()
        var skipChar: SkipCharType = SkipCharType.NONE
        loop@while(lastChar != '"' || skipChar == SkipCharType.START_NEW) {
            skipChar = when (skipChar) {
                SkipCharType.NONE -> when(lastChar) {
                    '\\' -> SkipCharType.START_NEW
                    else -> addCharAndResetSkipChar("$lastChar")
                }
                SkipCharType.START_NEW -> when(lastChar) {
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
                    'x' -> SkipCharType.NEW_UTF_CHAR('x', 2)
                    'u' -> SkipCharType.NEW_UTF_CHAR('u', 4)
                    'U' -> SkipCharType.NEW_UTF32_CHAR()
                    else -> addCharAndResetSkipChar("\\$lastChar")
                }
                is SkipCharType.NEW_UTF_CHAR -> when(lastChar.toLowerCase()) {
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

        currentReader = this.parentReader!!

        return this.jsonTokenConstructor(storedValue)
    }

    override fun handleReaderInterrupt(): JsonToken {
        throw InvalidJsonContent("Double quoted string was never closed")
    }
}