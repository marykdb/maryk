package maryk.core.json.yaml

import maryk.core.extensions.isLineBreak
import maryk.core.extensions.isSpacing
import maryk.core.json.JsonToken
import maryk.core.json.TokenType
import maryk.core.json.ValueType

private enum class ExplicitMapState {
    STARTED, INTERNAL_MAP, COMPLEX, SIMPLE, INTERRUPT_VALUE, EMPTY_KEY_VALUE
}

/** Reads Explicit map keys started with ? */
internal class ExplicitMapKeyReader(
    yamlReader: YamlReaderImpl,
    parentReader: MapItemsReader<*>
) : YamlCharWithParentAndIndentReader<MapItemsReader<*>>(yamlReader, parentReader),
    IsYamlCharWithIndentsReader
{
    private var state: ExplicitMapState? = null
    private var indentCount: Int = 0

    override fun readUntilToken(extraIndent: Int, tag: TokenType?): JsonToken {
        if (this.state == null) {
            this.state = ExplicitMapState.STARTED
            val isNewLine = this.lastChar.isLineBreak()
            val indentCount = this.yamlReader.skipEmptyLinesAndCommentsAndCountIndents()

            if (isNewLine) {
                if (indentCount <= this.parentReader.indentCount() + extraIndent) {
                    this.state = ExplicitMapState.EMPTY_KEY_VALUE
                    return this.endIndentLevel(indentCount, tag, null)
                }

                this.indentCount = indentCount
            } else {
                this.indentCount = indentCount + this.parentReader.indentCount() + extraIndent + 1
            }
        }

        val indentToAdd = if (this.state == ExplicitMapState.STARTED) 0 else 1

        this.selectReaderAndRead(false, tag, extraIndent + indentToAdd, this::jsonTokenCreator).let {
            if (this.state == ExplicitMapState.STARTED) {
                if (it !is JsonToken.FieldName) {
                    this.yamlReader.pushToken(it)
                    this.state = ExplicitMapState.COMPLEX
                    return JsonToken.StartComplexFieldName
                } else {
                    this.state = ExplicitMapState.SIMPLE
                }
            }
            return it
        }
    }

    override fun indentCount() = this.indentCount

    override fun endIndentLevel(
        indentCount: Int,
        tag: TokenType?,
        tokenToReturn: (() -> JsonToken)?
    ): JsonToken {
        this.currentReader = this

        if (this.indentCount == indentCount) {
            tokenToReturn?.let {
                this.yamlReader.setUnclaimedIndenting(indentCount)
                return it()
            }

            return this.continueIndentLevel(0, tag)
        }

        this.currentReader = this.parentReader
        this.yamlReader.setUnclaimedIndenting(indentCount)

        return when(this.state) {
            ExplicitMapState.INTERNAL_MAP -> {
                tokenToReturn?.let {
                    this.yamlReader.pushToken(JsonToken.EndObject)
                    this.yamlReader.pushToken(JsonToken.EndComplexFieldName)
                    return it()
                }
                this.yamlReader.pushToken(JsonToken.EndComplexFieldName)
                JsonToken.EndObject
            }
            ExplicitMapState.COMPLEX -> {
                tokenToReturn?.let {
                    return it().also {
                        yamlReader.pushToken(JsonToken.EndComplexFieldName)
                    }
                }
                JsonToken.EndComplexFieldName
            }
            null, ExplicitMapState.STARTED, ExplicitMapState.SIMPLE -> {
                tokenToReturn?.let {
                    return it()
                }
                this.parentReader.checkAndCreateFieldName(null, false)
            }
            ExplicitMapState.EMPTY_KEY_VALUE -> {
                if (this.lastChar == ':') {
                    read()
                    if(this.lastChar == ' ') {
                        return this.parentReader.checkAndCreateFieldName(null, false)
                    }
                }

                val value = JsonToken.Value(null, ValueType.Null)
                this.yamlReader.pushToken(value)
                this.parentReader.setState(value)
                this.parentReader.checkAndCreateFieldName(null, false)
            }
            ExplicitMapState.INTERRUPT_VALUE -> throw Exception("Should only happen on interrupts")
        }
    }

    override fun foundMap(tag: TokenType?, startedAtIndent: Int): JsonToken? {
        if (this.state != ExplicitMapState.INTERNAL_MAP && this.state != ExplicitMapState.COMPLEX) {
            this.state = ExplicitMapState.INTERNAL_MAP
            this.yamlReader.pushToken(JsonToken.SimpleStartObject)
            return JsonToken.StartComplexFieldName
        }

        return null
    }

    internal fun jsonTokenCreator(value: String?, isPlainStringReader: Boolean, tag: TokenType?, extraIndent: Int): JsonToken {
        while (this.lastChar.isSpacing()) {
            read()
        }

        if (this.lastChar == ':' && !this.yamlReader.hasUnclaimedIndenting()) {
            read()
            if (this.lastChar.isWhitespace()) {
                if (!this.lastChar.isLineBreak()) {
                    read()
                }

                return this.foundMap(tag, extraIndent)?.let {
                    this.yamlReader.pushToken(
                        this.checkAndCreateFieldName(value, isPlainStringReader)
                    )
                    it
                } ?: this.checkAndCreateFieldName(value, isPlainStringReader)
            } else {
                throw InvalidYamlContent("There should be whitespace after :")
            }
        } else if (this.state == ExplicitMapState.STARTED) {
            return this.parentReader.checkAndCreateFieldName(value, isPlainStringReader)
        }

        return createYamlValueToken(value, tag, isPlainStringReader)
    }

    override fun handleReaderInterrupt() =
        when(this.state) {
            null -> {
                this.state = ExplicitMapState.STARTED
                JsonToken.SimpleStartObject
            }
            ExplicitMapState.COMPLEX -> {
                this.state = ExplicitMapState.INTERRUPT_VALUE
                JsonToken.EndComplexFieldName
            }
            ExplicitMapState.INTERNAL_MAP -> {
                this.state = ExplicitMapState.COMPLEX
                JsonToken.EndObject
            }
            ExplicitMapState.EMPTY_KEY_VALUE, ExplicitMapState.STARTED, ExplicitMapState.SIMPLE -> {
                this.state = ExplicitMapState.INTERRUPT_VALUE
                JsonToken.FieldName(null)
            }
            ExplicitMapState.INTERRUPT_VALUE -> {
                this.currentReader = this.parentReader
                JsonToken.Value(null, ValueType.Null)
            }
        }
}
