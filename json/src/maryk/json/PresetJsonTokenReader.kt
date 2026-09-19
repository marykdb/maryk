package maryk.json

import maryk.json.JsonToken.EndArray
import maryk.json.JsonToken.EndDocument
import maryk.json.JsonToken.EndObject
import maryk.json.JsonToken.FieldName
import maryk.json.JsonToken.StartArray
import maryk.json.JsonToken.StartObject
import maryk.json.JsonToken.Stopped

/** Returns JsonTokens supplied by [tokens] */
class PresetJsonTokenReader(
    private val tokens: List<JsonToken>
) : IsJsonLikeReader {
    override var currentToken: JsonToken = tokens.firstOrNull() ?: EndDocument
    override var columnNumber = 0
    override var lineNumber = 0

    private var index = 0
    private var typeStackCount = 0

    override fun nextToken(): JsonToken {
        if (++index > tokens.lastIndex) {
            return EndDocument.also { currentToken = it }
        }

        return tokens[index].also {
            currentToken = it
            when (it) {
                is StartObject, is StartArray -> typeStackCount++
                is EndObject, is EndArray -> typeStackCount--
                else -> {}
            }
        }
    }

    override fun skipUntilNextField(handleSkipToken: ((JsonToken) -> Unit)?) {
        val startDepth = typeStackCount
        do {
            nextToken()
            handleSkipToken?.invoke(currentToken)
        } while (!(isFieldOrEndObject() && typeStackCount <= startDepth) && currentToken !is Stopped)
    }

    private fun isFieldOrEndObject() = currentToken is FieldName || currentToken is EndObject
}
