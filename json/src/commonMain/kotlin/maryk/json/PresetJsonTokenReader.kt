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
    override var currentToken: JsonToken = this.tokens[0]

    override var columnNumber = 0
    override var lineNumber = 0

    private var index = 1

    private var typeStackCount: Int = 0

    override fun nextToken(): JsonToken {
        if (index > tokens.lastIndex) {
            this.currentToken = EndDocument
            return this.currentToken
        }

        return this.tokens[index++].also {
            this.currentToken = it
            when (it) {
                is StartObject, is StartArray -> {
                    this.typeStackCount++
                }
                is EndObject, is EndArray -> {
                    this.typeStackCount--
                }
                else -> Unit
            }
        }
    }

    override fun skipUntilNextField(handleSkipToken: ((JsonToken) -> Unit)?) {
        val startDepth = this.typeStackCount
        do {
            nextToken()
            handleSkipToken?.invoke(this.currentToken)
        } while (
            !(currentToken is FieldName && this.typeStackCount <= startDepth)
            && currentToken !is Stopped
        )
    }
}
