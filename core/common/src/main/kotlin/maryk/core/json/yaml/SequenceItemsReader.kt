package maryk.core.json.yaml

import maryk.core.json.ArrayType
import maryk.core.json.JsonToken
import maryk.core.json.TokenType
import maryk.lib.extensions.isLineBreak

/** Reader for Sequence Items */
internal class SequenceItemsReader<out P>(
    yamlReader: YamlReaderImpl,
    parentReader: P,
    val indentToAdd: Int = 0
) : YamlCharWithParentAndIndentReader<P>(yamlReader, parentReader),
    IsYamlCharWithIndentsReader
        where P : YamlCharReader,
              P : IsYamlCharWithIndentsReader
{
    private var isStarted: Boolean? = null

    override fun readUntilToken(extraIndent: Int, tag: TokenType?): JsonToken {
        return when(this.isStarted) {
            null -> {
                this.isStarted = false
                tag?.let {
                    val sequenceType =
                        it as? ArrayType ?: throw InvalidYamlContent("Can only use sequence tags on sequences")
                    JsonToken.StartArray(sequenceType)
                } ?: JsonToken.SimpleStartArray
            }
            false -> {
                this.isStarted = true
                this.selectReaderAndRead(this.lastChar.isLineBreak(), tag, 1) { value, isPlainString, tagg, _ ->
                    createYamlValueToken(value, tagg, isPlainString)
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
                        this.selectReaderAndRead(isLineBreak, tag, currentIndentCount - this.indentCount()) { value, isPlainString, tagg, _ ->
                            createYamlValueToken(value, tagg, isPlainString)
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
        if (this.lastChar != '-') {
            val indentCount = this.indentCount()
            if (this.parentReader is MapItemsReader<*> && this.parentReader.indentCount() == indentCount) {
                this.yamlReader.setUnclaimedIndenting(indentCount)
                this.currentReader = this.parentReader
                return JsonToken.EndArray
            }
            throwSequenceException()
        }
        read()
        if (!this.lastChar.isWhitespace()) {
            throwSequenceException()
        }
        if (this.lastChar.isLineBreak()) {
            return this.readIndentsAndContinue(tag) {
                this.selectReaderAndRead(true, tag, it) { value, isPlainString, tagg, _ ->
                    createYamlValueToken(value, tagg, isPlainString)
                }
            }
        }

        return this.selectReaderAndRead(false, tag, 1 + extraIndent) { value, isPlainString, tagg, _ ->
            createYamlValueToken(value, tagg, isPlainString)
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
            JsonToken.EndArray
        } else {
            val returnFunction = tokenToReturn?.let {
                this.yamlReader.pushToken(JsonToken.EndArray)
                it
            } ?: { JsonToken.EndArray }

            this.currentReader = this.parentReader
            this.parentReader.endIndentLevel(indentCount, tag, returnFunction)
        }
    }

    override fun handleReaderInterrupt(): JsonToken {
        this.currentReader = this.parentReader
        return JsonToken.EndArray
    }

    private fun throwSequenceException() {
        throw InvalidYamlContent("Sequence was started on this indentation level, this is not an Sequence entry")
    }
}
