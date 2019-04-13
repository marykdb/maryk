package maryk.yaml

import maryk.json.ArrayType
import maryk.json.JsonToken
import maryk.json.TokenType
import maryk.lib.extensions.isLineBreak

/** Reader for Sequence Items */
internal class SequenceItemsReader<out P : IsYamlCharWithIndentsReader>(
    yamlReader: YamlReaderImpl,
    parentReader: P,
    val indentToAdd: Int = 0
) : YamlCharWithParentAndIndentReader<P>(yamlReader, parentReader),
    IsYamlCharWithIndentsReader {
    private var isStarted: Boolean? = null
    private var expectValueAfter: JsonToken? = null

    override fun readUntilToken(extraIndent: Int, tag: TokenType?): JsonToken {
        return when (this.isStarted) {
            null -> {
                this.isStarted = false
                tag?.let {
                    val sequenceType =
                        it as? ArrayType
                            ?: throw InvalidYamlContent("Can only use sequence tags on sequences")
                    JsonToken.StartArray(sequenceType)
                } ?: JsonToken.SimpleStartArray
            }
            false -> {
                this.isStarted = true
                this.expectValueAfter = this.yamlReader.currentToken
                this.selectReaderAndRead(this.lastChar.isLineBreak(), tag, 1) { value, isPlainString, tagg, _ ->
                    createYamlValueToken(value, tagg, isPlainString)
                }.also {
                    this.expectValueAfter = null
                }
            }
            else -> {
                val isLineBreak = this.lastChar.isLineBreak()
                val currentIndentCount = this.yamlReader.skipEmptyLinesAndCommentsAndCountIndents()
                val readerIndentCount = this.indentCount()
                when {
                    currentIndentCount < readerIndentCount -> // End current reader because indentation is lower
                        this.endIndentLevel(currentIndentCount, tag, null)
                    currentIndentCount == readerIndentCount -> // Continue reading on same level
                        this.continueIndentLevel(extraIndent, tag)
                    else -> // Deeper value
                        this.selectReaderAndRead(
                            isLineBreak,
                            tag,
                            currentIndentCount - this.indentCount()
                        ) { value, isPlainString, tagg, _ ->
                            createYamlValueToken(value, tagg, isPlainString)
                        }.also {
                            this.expectValueAfter = this.yamlReader.currentToken
                        }
                }
            }
        }
    }

    override fun foundMap(tag: TokenType?, startedAtIndent: Int): JsonToken {
        @Suppress("UNCHECKED_CAST")
        return MapItemsReader(
            this.yamlReader,
            this.currentReader as P,
            startedAtIndent
        ).let {
            this.currentReader = it
            it.readUntilToken(0, tag)
        }
    }

    override fun continueIndentLevel(extraIndent: Int, tag: TokenType?): JsonToken {
        if (this.expectValueAfter == this.yamlReader.currentToken && extraIndent == 0) {
            this.yamlReader.setUnclaimedIndenting(this.indentCount())
            return returnExpectedNullValue(tag)
        }

        if (this.lastChar != '-') {
            val indentCount = this.indentCount()
            if (this.parentReader is MapItemsReader<*> && this.parentReader.indentCount() == indentCount) {
                this.yamlReader.setUnclaimedIndenting(indentCount)
                this.currentReader = this.parentReader
                if (this.expectValueAfter != null) {
                    this.yamlReader.pushToken(JsonToken.EndArray)
                    return JsonToken.NullValue
                }
                return JsonToken.EndArray
            }
            throwSequenceException()
        }
        this.expectValueAfter = this.yamlReader.currentToken
        read()
        if (!this.lastChar.isWhitespace()) {
            throwSequenceException()
        }

        if (this.lastChar.isLineBreak()) {
            return this.readIndentsAndContinue(tag) {
                this.selectReaderAndRead(true, tag, it) { value, isPlainString, tagg, _ ->
                    createYamlValueToken(value, tagg, isPlainString)
                }.also {
                    this.expectValueAfter = null
                }
            }
        }

        return this.selectReaderAndRead(false, tag, 1 + extraIndent) { value, isPlainString, tagg, _ ->
            createYamlValueToken(value, tagg, isPlainString)
        }.also {
            this.expectValueAfter = null
        }
    }

    override fun indentCount() = this.parentReader.indentCount() + this.indentToAdd

    override fun endIndentLevel(
        indentCount: Int,
        tag: TokenType?,
        tokenToReturn: (() -> JsonToken)?
    ): JsonToken {
        if (indentCount == this.indentCount()) {
            // this reader should handle the read
            this.currentReader = this

            tokenToReturn?.let {
                this.yamlReader.setUnclaimedIndenting(indentCount)
                return it()
            }

            this.yamlReader.setUnclaimedIndenting(null)
            this.continueIndentLevel(0, null)
        }

        return if (indentToAdd > 0) {
            this.yamlReader.setUnclaimedIndenting(indentCount)
            this.currentReader = this.parentReader
            tokenToReturn?.let {
                this.yamlReader.pushToken(JsonToken.EndArray)
                return it()
            }

            if (this.expectValueAfter == this.yamlReader.currentToken) {
                this.yamlReader.pushToken(JsonToken.EndArray)
                return returnExpectedNullValue(tag)
            }

            JsonToken.EndArray
        } else {
            val returnFunction = tokenToReturn?.let {
                this.yamlReader.pushToken(JsonToken.EndArray)
                it
            } ?: {
                if (this.expectValueAfter == this.yamlReader.currentToken) {
                    this.yamlReader.pushToken(JsonToken.EndArray)
                    returnExpectedNullValue(tag)
                } else {
                    JsonToken.EndArray
                }
            }

            this.currentReader = this.parentReader
            this.parentReader.endIndentLevel(indentCount, tag, returnFunction)
        }
    }

    private fun returnExpectedNullValue(tag: TokenType?): JsonToken {
        this.expectValueAfter = null

        return this.createTokensFittingTag(tag)
    }

    override fun handleReaderInterrupt(): JsonToken {
        if (this.expectValueAfter == this.yamlReader.currentToken) {
            this.yamlReader.hasException
            this.expectValueAfter = null
            return JsonToken.NullValue
        }

        this.currentReader = this.parentReader
        return JsonToken.EndArray
    }

    private fun throwSequenceException() {
        throw InvalidYamlContent("Sequence was started on this indentation level, this is not an Sequence entry")
    }
}
