package maryk.yaml

import maryk.json.JsonToken
import maryk.json.JsonToken.EndComplexFieldName
import maryk.json.JsonToken.EndObject
import maryk.json.JsonToken.FieldName
import maryk.json.JsonToken.SimpleStartObject
import maryk.json.JsonToken.StartComplexFieldName
import maryk.json.TokenType
import maryk.lib.extensions.isLineBreak
import maryk.lib.extensions.isSpacing
import maryk.yaml.ExplicitMapState.COMPLEX
import maryk.yaml.ExplicitMapState.EMPTY_KEY_VALUE
import maryk.yaml.ExplicitMapState.INTERNAL_MAP
import maryk.yaml.ExplicitMapState.SIMPLE
import maryk.yaml.ExplicitMapState.STARTED

private enum class ExplicitMapState {
    STARTED, INTERNAL_MAP, COMPLEX, SIMPLE, EMPTY_KEY_VALUE
}

/** Reads Explicit map keys started with ? */
internal class ExplicitMapKeyReader(
    yamlReader: YamlReaderImpl,
    parentReader: MapItemsReader<*>
) : YamlCharWithParentAndIndentReader<MapItemsReader<*>>(yamlReader, parentReader),
    IsYamlCharWithIndentsReader {
    private var state: ExplicitMapState? = null
    private var indentCount: Int = 0

    override fun readUntilToken(extraIndent: Int, tag: TokenType?): JsonToken {
        if (this.state == null) {
            this.state = STARTED
            val isNewLine = this.lastChar.isLineBreak()
            val indentCount = this.yamlReader.skipEmptyLinesAndCommentsAndCountIndents()

            if (isNewLine) {
                if (indentCount <= this.parentReader.indentCount() + extraIndent) {
                    this.state = EMPTY_KEY_VALUE
                    return this.endIndentLevel(indentCount, tag, null)
                }

                this.indentCount = indentCount
            } else {
                this.indentCount = indentCount + this.parentReader.indentCount() + extraIndent + 1
            }
        }

        val indentToAdd = if (this.state == STARTED) 0 else 1

        this.selectReaderAndRead(false, tag, extraIndent + indentToAdd, this::jsonTokenCreator).let {
            if (this.state == STARTED) {
                if (it !is FieldName) {
                    this.yamlReader.pushToken(it)
                    this.state = COMPLEX
                    return StartComplexFieldName
                } else {
                    this.state = SIMPLE
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

        return when (this.state) {
            INTERNAL_MAP -> {
                tokenToReturn?.let {
                    this.yamlReader.pushToken(EndObject)
                    this.yamlReader.pushToken(EndComplexFieldName)
                    return it()
                }
                this.yamlReader.pushToken(EndComplexFieldName)
                EndObject
            }
            COMPLEX -> {
                tokenToReturn?.let {
                    return it().also {
                        yamlReader.pushToken(EndComplexFieldName)
                    }
                }
                EndComplexFieldName
            }
            null, STARTED, SIMPLE -> {
                tokenToReturn?.let {
                    return it()
                }
                this.parentReader.checkAndCreateFieldName(null, false)
            }
            EMPTY_KEY_VALUE -> {
                if (this.lastChar == ':') {
                    read()
                    if (this.lastChar == ' ') {
                        return this.parentReader.checkAndCreateFieldName(null, false)
                    }
                }

                val value = JsonToken.NullValue
                this.yamlReader.pushToken(value)
                this.parentReader.setState(value)
                this.parentReader.checkAndCreateFieldName(null, false)
            }
        }
    }

    override fun foundMap(tag: TokenType?, startedAtIndent: Int): JsonToken? {
        if (this.state != INTERNAL_MAP && this.state != COMPLEX) {
            this.state = INTERNAL_MAP
            this.yamlReader.pushToken(SimpleStartObject)
            return StartComplexFieldName
        }

        return null
    }

    private fun jsonTokenCreator(
        value: String?,
        isPlainStringReader: Boolean,
        tag: TokenType?,
        extraIndent: Int
    ): JsonToken {
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
        } else if (this.state == STARTED) {
            return this.parentReader.checkAndCreateFieldName(value, isPlainStringReader)
        }

        return createYamlValueToken(value, tag, isPlainStringReader)
    }

    override fun handleReaderInterrupt() =
        when (this.state) {
            null -> {
                this.state = STARTED
                SimpleStartObject
            }
            COMPLEX -> {
                this.currentReader = this.parentReader
                EndComplexFieldName
            }
            INTERNAL_MAP -> {
                this.state = COMPLEX
                EndObject
            }
            EMPTY_KEY_VALUE, STARTED, SIMPLE -> {
                this.currentReader = this.parentReader
                FieldName(null)
            }
        }
}
