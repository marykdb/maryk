package io.maryk.cli

/**
 * Minimal command line parser that supports whitespace separated tokens and quoted arguments.
 */
internal object CommandLineParser {
    fun parse(input: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()

        var inQuotes = false
        var quoteChar = '"'
        var escaping = false

        input.forEach { char ->
            when {
                escaping -> {
                    current.append(char)
                    escaping = false
                }
                char == '\\' -> {
                    escaping = true
                }
                inQuotes && char == quoteChar -> {
                    inQuotes = false
                }
                inQuotes -> {
                    current.append(char)
                }
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
                else -> {
                    current.append(char)
                }
            }
        }

        if (escaping) {
            current.append('\\')
        }

        if (current.isNotEmpty()) {
            tokens.add(current.toString())
        }

        return tokens
    }

    private fun StringBuilder.isNotEmpty(): Boolean = this.length > 0
}
