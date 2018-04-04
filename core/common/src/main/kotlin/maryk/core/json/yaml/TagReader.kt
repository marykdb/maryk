package maryk.core.json.yaml

import maryk.core.json.JsonToken
import maryk.core.json.TokenType

/** Reads tags */
internal fun YamlCharReader.tagReader(onDone: (tag: TokenType) -> JsonToken): JsonToken {
    read()

    var prefix = ""
    var newTag = ""

    while (!this.lastChar.isWhitespace()) {
        if (this.lastChar == '!') {
            // Double !!
            if (prefix.isEmpty()) {
                prefix = "!$newTag!"
                newTag = ""
            } else {
                throw InvalidYamlContent("Invalid tag $newTag")
            }
        } else {
            newTag += this.lastChar
        }
        read()
    }
    // Single !
    if (prefix.isEmpty()) {
        prefix = "!"
    }

    return onDone(this.yamlReader.resolveTag(prefix, newTag))
}
