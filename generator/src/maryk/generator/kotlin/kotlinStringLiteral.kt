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
                '\b' -> append("\\b")
                '$' -> append("\\$")
                else -> if (char.isISOControl()) {
                    append("\\u")
                    append(char.code.toString(16).padStart(4, '0'))
                } else {
                    append(char)
                }
            }
        }
        append('"')
    }
