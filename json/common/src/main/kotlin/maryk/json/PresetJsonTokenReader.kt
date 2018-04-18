package maryk.json

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
            this.currentToken = JsonToken.EndDocument
            return this.currentToken
        }

        return this.tokens[index++].also {
            this.currentToken = it
            when (it) {
                is JsonToken.StartObject, is JsonToken.StartArray -> {
                    this.typeStackCount++
                }
                is JsonToken.EndObject, is JsonToken.EndArray -> {
                    this.typeStackCount--
                }
                else -> {}
            }
        }
    }

    override fun skipUntilNextField(handleSkipToken: ((JsonToken) -> Unit)?) {
        val startDepth = this.typeStackCount
        do {
            nextToken()
            handleSkipToken?.invoke(this.currentToken)
        } while (
            !(currentToken is JsonToken.FieldName && this.typeStackCount <= startDepth)
            && currentToken !is JsonToken.Stopped
        )
    }
}
