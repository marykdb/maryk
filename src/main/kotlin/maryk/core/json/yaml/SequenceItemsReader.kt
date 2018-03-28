package maryk.core.json.yaml

import maryk.core.json.ArrayType
import maryk.core.json.JsonToken
import maryk.core.json.TokenType

/** Reader for Sequence Items */
internal class SequenceItemsReader<out P>(
    yamlReader: YamlReaderImpl,
    parentReader: P,
    val indentToAdd: Int = 0
) : YamlCharWithParentReader<P>(yamlReader, parentReader),
    IsYamlCharWithIndentsReader,
    IsYamlCharWithChildrenReader
        where P : YamlCharReader,
              P : IsYamlCharWithChildrenReader,
              P : IsYamlCharWithIndentsReader
{
    private var isStarted = false

    override fun readUntilToken(tag: TokenType?): JsonToken {
        return if (!this.isStarted) {
            createLineReader(this)

            this.isStarted = true
            return tag?.let {
                val sequenceType = it as? ArrayType ?: throw InvalidYamlContent("Can only use sequence tags on sequences")
                JsonToken.StartArray(sequenceType)
            } ?: JsonToken.SimpleStartArray
        } else {
            IndentReader(
                yamlReader, this
            ).let {
                this.currentReader = it
                it.readUntilToken()
            }
        }
    }

    override fun foundMap(isExplicitMap: Boolean, tag: TokenType?): JsonToken {
        @Suppress("UNCHECKED_CAST")
        return MapItemsReader(
            this.yamlReader,
            this.currentReader as P,
            isExplicitMap
        ).let {
            this.currentReader = it
            it.readUntilToken(tag)
        }
    }

    override fun checkAndCreateFieldName(fieldName: String?, isPlainStringReader: Boolean) =
        this.parentReader.checkAndCreateFieldName(fieldName, isPlainStringReader)

    override fun isWithinMap() = this.parentReader.isWithinMap()

    override fun <P> newIndentLevel(indentCount: Int, parentReader: P, tag: TokenType?): JsonToken
            where P : YamlCharReader,
                  P : IsYamlCharWithChildrenReader,
                  P : IsYamlCharWithIndentsReader {
        this.createLineReader(parentReader)
        return this.currentReader.readUntilToken(tag)
    }

    override fun continueIndentLevel(tag: TokenType?): JsonToken {
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
        read()

        return createLineReader(this).readUntilToken(tag)
    }

    override fun indentCount() = this.parentReader.indentCountForChildren() + this.indentToAdd

    override fun indentCountForChildren() = this.indentCount() + 1

    override fun endIndentLevel(
        indentCount: Int,
        tag: TokenType?,
        tokenToReturn: (() -> JsonToken)?
    ): JsonToken {
        val correction = if(this.isWithinMap()) -1 else 0
        if (indentCount == this.indentCount() + correction) {
            // this reader should handle the read
            this.currentReader = this
            return if (tokenToReturn != null) {
                this.yamlReader.setUnclaimedIndenting(indentCount)
                tokenToReturn()
            } else {
                this.yamlReader.setUnclaimedIndenting(null)
                this.continueIndentLevel(null)
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

    override fun childIsDoneReading(closeLineReader: Boolean) {
        this.currentReader = this
    }

    override fun handleReaderInterrupt(): JsonToken {
        this.currentReader = this.parentReader
        return JsonToken.EndArray
    }

    private fun throwSequenceException() {
        throw InvalidYamlContent("Sequence was started on this indentation level, this is not an Sequence entry")
    }

    private fun <P> createLineReader(parentReader: P)
            where P : maryk.core.json.yaml.YamlCharReader,
                  P : maryk.core.json.yaml.IsYamlCharWithChildrenReader,
                  P : maryk.core.json.yaml.IsYamlCharWithIndentsReader = LineReader(
        yamlReader = yamlReader,
        parentReader = parentReader
    ).apply {
        this.currentReader = this
    }
}
