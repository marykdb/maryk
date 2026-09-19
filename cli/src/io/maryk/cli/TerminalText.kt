package io.maryk.cli

internal fun String.toTerminalText(): String = buildString {
    this@toTerminalText.forEach { character ->
        when (character) {
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\u001B' -> append("\\u001b")
            else -> {
                if (character.code < 0x20 || character.code == 0x7f) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
    }
}
