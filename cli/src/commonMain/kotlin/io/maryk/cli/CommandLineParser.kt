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
        val trimmed = input.trimStart()
        if (trimmed.equals("sql", ignoreCase = true) || trimmed.take(3).equals("sql", ignoreCase = true) && trimmed.getOrNull(3)?.isWhitespace() == true) {
            return parseSql(trimmed.drop(3).trimStart())
        }
        return tokenize(input)
    }

    /** SQL has its own quoting/comment rules; only tokenize command options before it. */
    private fun parseSql(arguments: String): ParseResult {
        val tokens = mutableListOf("sql")
        var remaining = arguments
        while (remaining.startsWith("--")) {
            val option = remaining.takeWhile { !it.isWhitespace() }
            if (option == "--file") {
                return when (val parsed = tokenize(remaining)) {
                    is ParseResult.Success -> ParseResult.Success(tokens + parsed.tokens)
                    is ParseResult.Error -> parsed
                }
            }
            tokens += option
            remaining = remaining.drop(option.length).trimStart()
            if (option == "--to-version") {
                val version = remaining.takeWhile { !it.isWhitespace() }
                if (version.isEmpty()) return ParseResult.Error("--to-version requires a version")
                tokens += version
                remaining = remaining.drop(version.length).trimStart()
            }
            if (option == "--") break
            if (option !in setOf("--snapshot", "--include-deleted", "--no-table-scan", "--to-version")) break
        }
        if (remaining.isNotEmpty()) tokens += remaining
        return ParseResult.Success(tokens)
    }

    private fun tokenize(input: String): ParseResult {
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
