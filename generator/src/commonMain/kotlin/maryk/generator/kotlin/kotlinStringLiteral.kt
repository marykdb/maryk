package maryk.generator.kotlin

internal fun String.kotlinStringLiteral(): String =
    buildString(length + 2) {
        append('"')
        for (char in this@kotlinStringLiteral) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '$' -> append("\\$")
                else -> append(char)
            }
        }
        append('"')
    }
