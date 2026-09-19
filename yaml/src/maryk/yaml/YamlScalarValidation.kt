package maryk.yaml

internal fun validateRawYamlScalarCharacter(character: Char, allowQuotedNonC0: Boolean = false) {
    if (
        character.code in 0x00..0x08 ||
        character.code in 0x0B..0x1F ||
        character.code == 0x7F ||
        character.code in 0x80..0x9F && character != '\u0085' && !allowQuotedNonC0
    ) {
        throw InvalidYamlContent("YAML scalar contains a forbidden control character")
    }
}

internal fun validateYamlScalar(value: String) {
    var index = 0
    while (index < value.length) {
        val character = value[index]
        when {
            character.isHighSurrogate() -> {
                if (index + 1 >= value.length || !value[index + 1].isLowSurrogate()) {
                    throw InvalidYamlContent("YAML scalar contains an unpaired UTF-16 surrogate")
                }
                index++
            }
            character.isLowSurrogate() -> {
                throw InvalidYamlContent("YAML scalar contains an unpaired UTF-16 surrogate")
            }
        }
        index++
    }
}
