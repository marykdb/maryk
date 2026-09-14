package maryk.sql.syntax

internal enum class SqlTokenKind {
    IDENTIFIER,
    QUOTED_IDENTIFIER,
    STRING,
    NUMBER,
    PARAMETER,
    SYMBOL,
    EOF,
}

internal data class SqlToken(
    val kind: SqlTokenKind,
    val text: String,
    val position: Int,
) {
    fun isKeyword(keyword: String): Boolean =
        kind == SqlTokenKind.IDENTIFIER && text.equalsAsciiIgnoreCase(keyword)
}

internal const val DEFAULT_MAX_SQL_TOKENS = 16_384
internal const val DEFAULT_MAX_SQL_PARAMETERS = 1_024
private const val MAX_SQL_LENGTH = 64 * 1024

/** Applies the lexical resource limits shared by read and write statements. */
internal fun validateSqlText(text: String) {
    val tokens = SqlLexer(text, DEFAULT_MAX_SQL_TOKENS).tokenize()
    if (tokens.count { it.kind == SqlTokenKind.PARAMETER } > DEFAULT_MAX_SQL_PARAMETERS) {
        throw SqlParseException("Parameter limit of $DEFAULT_MAX_SQL_PARAMETERS exceeded", text.length)
    }
}

internal class SqlLexer(
    private val text: String,
    private val maxTokens: Int,
) {
    private var index = 0
    private val tokens = mutableListOf<SqlToken>()

    fun tokenize(): List<SqlToken> {
        if (text.length > MAX_SQL_LENGTH) {
            fail("SQL text exceeds the $MAX_SQL_LENGTH character limit", MAX_SQL_LENGTH)
        }
        if (maxTokens < 1) fail("Token limit must be positive", 0)
        validateSurrogates()

        while (index < text.length) {
            when {
                text[index].isWhitespace() -> index++
                startsWith("--") -> skipLineComment()
                startsWith("/*") -> skipBlockComment()
                text[index] == '\'' -> readDelimited(SqlTokenKind.STRING, '\'')
                text[index] == '"' -> readDelimited(SqlTokenKind.QUOTED_IDENTIFIER, '"')
                text[index] == '?' -> addToken(SqlTokenKind.PARAMETER, "?", index++)
                text[index].isDigit() || text[index] == '.' && peekChar(1)?.isDigit() == true -> readNumber()
                text[index].isIdentifierStart() -> readIdentifier()
                else -> readSymbol()
            }
        }
        tokens += SqlToken(SqlTokenKind.EOF, "", text.length)
        return tokens
    }

    private fun validateSurrogates() {
        var characterIndex = 0
        while (characterIndex < text.length) {
            val code = text[characterIndex].code
            when {
                code in HIGH_SURROGATE_RANGE -> {
                    if (characterIndex + 1 >= text.length || text[characterIndex + 1].code !in LOW_SURROGATE_RANGE) {
                        fail("Unpaired high surrogate", characterIndex)
                    }
                    characterIndex += 2
                }
                code in LOW_SURROGATE_RANGE -> fail("Unpaired low surrogate", characterIndex)
                else -> characterIndex++
            }
        }
    }

    private fun skipLineComment() {
        index += 2
        while (index < text.length && text[index] != '\n' && text[index] != '\r') index++
    }

    private fun skipBlockComment() {
        val position = index
        index += 2
        while (index + 1 < text.length && !startsWith("*/")) index++
        if (index + 1 >= text.length) fail("Unterminated block comment", position)
        index += 2
    }

    private fun readDelimited(kind: SqlTokenKind, delimiter: Char) {
        val position = index++
        val value = StringBuilder()
        while (index < text.length) {
            val character = text[index++]
            if (character != delimiter) {
                value.append(character)
            } else if (index < text.length && text[index] == delimiter) {
                value.append(delimiter)
                index++
            } else {
                addToken(kind, value.toString(), position)
                return
            }
        }
        val description = if (kind == SqlTokenKind.STRING) "string literal" else "quoted identifier"
        fail("Unterminated $description", position)
    }

    private fun readNumber() {
        val position = index
        if (text[index] == '.') index++
        while (peekChar()?.isDigit() == true) index++
        if (peekChar() == '.' && peekChar(1) != '.') {
            index++
            while (peekChar()?.isDigit() == true) index++
        }
        if (peekChar() == 'e' || peekChar() == 'E') {
            val exponentPosition = index++
            if (peekChar() == '+' || peekChar() == '-') index++
            if (peekChar()?.isDigit() != true) fail("Expected exponent digits", exponentPosition)
            while (peekChar()?.isDigit() == true) index++
        }
        if (peekChar()?.isIdentifierStart() == true) {
            fail("Invalid character after numeric literal", index)
        }
        addToken(SqlTokenKind.NUMBER, text.substring(position, index), position)
    }

    private fun readIdentifier() {
        val position = index++
        while (peekChar()?.isIdentifierPart() == true) index++
        addToken(SqlTokenKind.IDENTIFIER, text.substring(position, index), position)
    }

    private fun readSymbol() {
        val position = index
        val twoCharacters = if (index + 1 < text.length) text.substring(index, index + 2) else ""
        val symbol = if (twoCharacters in DOUBLE_SYMBOLS) {
            index += 2
            twoCharacters
        } else {
            text[index++].toString()
        }
        addToken(SqlTokenKind.SYMBOL, symbol, position)
    }

    private fun addToken(kind: SqlTokenKind, value: String, position: Int) {
        if (tokens.size >= maxTokens) fail("Token limit of $maxTokens exceeded", position)
        tokens += SqlToken(kind, value, position)
    }

    private fun startsWith(value: String): Boolean = text.startsWith(value, index)

    private fun peekChar(offset: Int = 0): Char? = text.getOrNull(index + offset)

    private fun fail(message: String, position: Int): Nothing =
        throw SqlParseException("$message at position $position", position)

    private companion object {
        val DOUBLE_SYMBOLS = setOf("<=", ">=", "<>", "!=", "||")
        val HIGH_SURROGATE_RANGE = 0xD800..0xDBFF
        val LOW_SURROGATE_RANGE = 0xDC00..0xDFFF
    }
}

private fun Char.isIdentifierStart(): Boolean = this == '_' || isLetter() || code >= 0x80

private fun Char.isIdentifierPart(): Boolean = isIdentifierStart() || isDigit() || this == '$'

private fun String.equalsAsciiIgnoreCase(other: String): Boolean {
    if (length != other.length) return false
    for (index in indices) {
        val left = this[index].asciiLowercase()
        val right = other[index].asciiLowercase()
        if (left != right) return false
    }
    return true
}

private fun Char.asciiLowercase(): Char = if (this in 'A'..'Z') this + ('a' - 'A') else this
