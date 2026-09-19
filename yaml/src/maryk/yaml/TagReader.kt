package maryk.yaml

import maryk.json.ArrayType
import maryk.json.ExceptionWhileReadingJson
import maryk.json.JsonToken
import maryk.json.JsonToken.EndArray
import maryk.json.JsonToken.EndObject
import maryk.json.JsonToken.NullValue
import maryk.json.JsonToken.StartArray
import maryk.json.JsonToken.StartObject
import maryk.json.JsonToken.Value
import maryk.json.MapType
import maryk.json.TokenType
import maryk.json.ValueType.IsNullValueType

private const val MAX_YAML_TAG_LENGTH = 1024

/** Reads tags */
internal fun IsYamlCharReader.tagReader(onDone: (tag: TokenType) -> JsonToken): JsonToken {
    read()

    var prefix: String? = null
    val newTag = StringBuilder()
    var foundUrlTag = false
    var tagLength = 0

    try {
        while (!this.lastChar.isWhitespace() && (foundUrlTag || this.lastChar != ',')) {
            tagLength++
            if (tagLength > MAX_YAML_TAG_LENGTH) {
                throw InvalidYamlContent(
                    "Tag length budget exceeded: $tagLength > $MAX_YAML_TAG_LENGTH"
                )
            }
            if (this.lastChar == '<' && newTag.isEmpty()) {
                foundUrlTag = true
            }

            if (this.lastChar == '!') {
                // Double !!
                if (prefix == null) {
                    prefix = "!$newTag!"
                    newTag.clear()
                } else {
                    throw InvalidYamlContent("Invalid tag $newTag")
                }
            } else {
                newTag.append(this.lastChar)
            }
            read()
        }
    } catch (e: ExceptionWhileReadingJson) {
        this.yamlReader.hasException = true
    }

    if (foundUrlTag && newTag.lastOrNull() != '>') {
        throw InvalidYamlContent("Yaml URL tag should always end with '>'")
    }

    // Single !
    val tag = this.yamlReader.resolveTag(prefix ?: "!", newTag.toString())

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

internal fun IsYamlCharReader.createTokensFittingTag(tag: TokenType?): JsonToken =
    when (tag) {
        is MapType -> {
            this.yamlReader.pushTokenAsFirst(EndObject)
            StartObject(tag)
        }
        is ArrayType -> {
            this.yamlReader.pushTokenAsFirst(EndArray)
            StartArray(tag)
        }
        is IsNullValueType -> Value(null, tag)
        else -> NullValue
    }
