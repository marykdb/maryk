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
        var hasArgument = false
        var quoteChar = '"'
        var index = 0
        while (index < input.length) {
            val char = input[index]
            when {
                char == '\\' && inQuotes && input.getOrNull(index + 1) in setOf(quoteChar, '\\') -> {
                    current.append(input[++index])
                    hasArgument = true
                }
                inQuotes && char == quoteChar -> inQuotes = false
                inQuotes -> {
                    current.append(char)
                    hasArgument = true
                }
                char == '"' || char == '\'' -> {
                    inQuotes = true
                    quoteChar = char
                    hasArgument = true
                }
                char.isWhitespace() -> {
                    if (hasArgument) {
                        tokens.add(current.toString())
                        current.setLength(0)
                        hasArgument = false
                    }
                }
                else -> {
                    current.append(char)
                    hasArgument = true
                }
            }
            index++
        }

        if (inQuotes) {
            return ParseResult.Error("Missing closing $quoteChar quote.")
        }

        if (hasArgument) {
            tokens.add(current.toString())
        }

        return ParseResult.Success(tokens)
    }
}
