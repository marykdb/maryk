package maryk.generator.proto3

private val proto3Identifier = Regex("[A-Za-z_][A-Za-z0-9_]*")

internal fun String.requireProto3Identifier(): String {
    require(matches(proto3Identifier)) { "Proto3 identifier is invalid: $this" }
    return this
}

internal fun UInt.requireProto3FieldNumber(): UInt {
    require(this in 1u..536_870_911u && this !in 19_000u..19_999u) {
        "Proto3 field number is invalid: $this"
    }
    return this
}

internal fun String.proto3StringLiteral(): String = buildString {
    for (character in this@proto3StringLiteral) {
        when (character) {
            '\\' -> append("\\\\")
            '\"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\u0008' -> append("\\b")
            '\u000c' -> append("\\f")
            in '\u0000'..'\u001f' -> {
                append("\\x")
                append(character.code.toString(16).padStart(2, '0'))
            }
            in '\u007f'..'\u009f' -> {
                append("\\u")
                append(character.code.toString(16).padStart(4, '0'))
            }
            else -> append(character)
        }
    }
}
