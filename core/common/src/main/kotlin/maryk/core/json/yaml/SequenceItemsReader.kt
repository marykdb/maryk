package maryk.core.json.yaml

import maryk.core.extensions.isLineBreak
import maryk.core.json.ArrayType
import maryk.core.json.JsonToken
import maryk.core.json.TokenType

/** Reader for Sequence Items */
internal class SequenceItemsReader<out P>(
    yamlReader: YamlReaderImpl,
    parentReader: P,
    val indentToAdd: Int = 0
) : YamlCharWithParentAndIndentReader<P>(yamlReader, parentReader),
    IsYamlCharWithIndentsReader,
    IsYamlCharWithChildrenReader
        where P : YamlCharReader,
              P : IsYamlCharWithChildrenReader,
              P : IsYamlCharWithIndentsReader
{
    private var isStarted: Boolean? = null

    override fun readUntilToken(extraIndent: Int, tag: TokenType?): JsonToken {
        return if (this.isStarted == null) {
            this.isStarted = false
            return tag?.let {
                val sequenceType =
                    it as? ArrayType ?: throw InvalidYamlContent("Can only use sequence tags on sequences")
                JsonToken.StartArray(sequenceType)
            } ?: JsonToken.SimpleStartArray
        } else if (this.isStarted == false) {
            this.isStarted = true
            return this.newLineReader(this.lastChar.isLineBreak(), tag, 0) { value, isPlainString, tagg ->
                createYamlValueToken(value, tagg, isPlainString)
            }
        } else {
            val isLineBreak = this.lastChar.isLineBreak()
            val currentIndentCount = this.yamlReader.skipEmptyLinesAndCommentsAndCountIndents()
            val readerIndentCount = this.indentCount()
            if (currentIndentCount < readerIndentCount) {
                // End current reader because indentation is lower
                return this.endIndentLevel(currentIndentCount, tag, null)
            } else if (currentIndentCount == readerIndentCount) {
                // Continue reading on same level
                this.continueIndentLevel(extraIndent, tag)
            } else {
                // Deeper value
                this.newLineReader(isLineBreak, tag, currentIndentCount - this.indentCountForChildren()) { value, isPlainString, tagg ->
                    createYamlValueToken(value, tagg, isPlainString)
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

    override fun isWithinMap() = false

    override fun continueIndentLevel(extraIndent: Int, tag: TokenType?): JsonToken {
        if (this.lastChar != '-') {
            val indentCount = this.indentCount()
            if (this.parentReader.isWithinMap() && this.parentReader.indentCount() == indentCount) {
                this.yamlReader.setUnclaimedIndenting(indentCount)
                this.parentReader.childIsDoneReading(false)
                return JsonToken.EndArray
            }
            throwSequenceException()
        }
        read()
        if (!this.lastChar.isWhitespace()) {
            throwSequenceException()
        }
        if (this.lastChar.isLineBreak()) {
            return IndentReader(
                yamlReader, this
            ).let {
                this.currentReader = it
                it.readUntilToken(0)
            }
        }

        read()

        return this.newLineReader(false, tag, 0) { value, isPlainString, tagg ->
            createYamlValueToken(value, tagg, isPlainString)
        }
    }

    override fun indentCount() = this.indentToAdd  + if(this.parentReader is MapItemsReader<*>) this.parentReader.indentCount() else this.parentReader.indentCountForChildren()

    override fun indentCountForChildren() = this.indentCount() + 1

    override fun endIndentLevel(
        indentCount: Int,
        tag: TokenType?,
        tokenToReturn: (() -> JsonToken)?
    ): JsonToken {
        if (indentCount == this.indentCount()) {
            // this reader should handle the read
            this.currentReader = this
            return if (tokenToReturn != null) {
                this.yamlReader.setUnclaimedIndenting(indentCount)
                tokenToReturn()
            } else {
                this.yamlReader.setUnclaimedIndenting(null)
                this.continueIndentLevel(0, null)
            }
        }

        return if (indentToAdd > 0) {
            this.yamlReader.setUnclaimedIndenting(indentCount)
            this.parentReader.childIsDoneReading(false)
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
