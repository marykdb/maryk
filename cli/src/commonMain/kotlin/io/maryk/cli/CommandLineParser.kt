package io.maryk.cli

/**
 * Minimal command line parser that supports whitespace separated tokens and quoted arguments.
 */
internal object CommandLineParser {
    sealed interface ParseResult {
        data class Success(val tokens: List<String>) : ParseResult
        data class Error(val message: String) : ParseResult
    }

    fun parse(input: String): ParseResult {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()

        var inQuotes = false
        var quoteChar = '"'
        var index = 0
        while (index < input.length) {
            val char = input[index]
            when {
                char == '\\' && inQuotes && input.getOrNull(index + 1) in setOf(quoteChar, '\\') -> {
                    current.append(input[++index])
                }
                inQuotes && char == quoteChar -> inQuotes = false
                inQuotes -> current.append(char)
                char == '"' || char == '\'' -> {
                    inQuotes = true
                    quoteChar = char
                }
                char.isWhitespace() -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current.setLength(0)
                    }
                }
                else -> current.append(char)
            }
            index++
        }

        if (inQuotes) {
            return ParseResult.Error("Missing closing $quoteChar quote.")
        }

        if (current.isNotEmpty()) {
            tokens.add(current.toString())
        }

        return ParseResult.Success(tokens)
    }

    private fun StringBuilder.isNotEmpty(): Boolean = this.length > 0
}
