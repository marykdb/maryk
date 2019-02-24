package maryk.yaml

import maryk.json.ArrayType
import maryk.json.ExceptionWhileReadingJson
import maryk.json.JsonToken
import maryk.json.MapType
import maryk.json.TokenType
import maryk.json.ValueType

/** Reads tags */
internal fun YamlCharReader.tagReader(onDone: (tag: TokenType) -> JsonToken): JsonToken {
    read()

    var prefix = ""
    var newTag = ""
    var foundUrlTag = false

    try {
        while (!this.lastChar.isWhitespace() && (foundUrlTag || this.lastChar != ',')) {
            if (this.lastChar == '<' && newTag.isEmpty()) {
                foundUrlTag = true
            }

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
    } catch (e: ExceptionWhileReadingJson) {
        this.yamlReader.hasException = true
    }

    if (foundUrlTag && newTag.last() != '>') {
        throw InvalidYamlContent("Yaml URL tag should always end with '>'")
    }

    // Single !
    if (prefix.isEmpty()) {
        prefix = "!"
    }

    val tag = this.yamlReader.resolveTag(prefix, newTag)

    // Handle exception by creating fitting placeholder tag
    if (this.yamlReader.hasException) {
        return createTokensFittingTag(tag)
    }
    return try {
        onDone(tag)
    } catch (e: ExceptionWhileReadingJson) {
        this.yamlReader.hasException = true
        createTokensFittingTag(tag)
    }
}

internal fun YamlCharReader.createTokensFittingTag(tag: TokenType?): JsonToken {
    return when (tag) {
        is MapType -> {
            this.yamlReader.pushTokenAsFirst(JsonToken.EndObject)
            JsonToken.StartObject(tag)
        }
        is ArrayType -> {
            this.yamlReader.pushTokenAsFirst(JsonToken.EndArray)
            JsonToken.StartArray(tag)
        }
        is ValueType.IsNullValueType -> JsonToken.Value(null, tag)
        else -> JsonToken.NullValue
    }
}
