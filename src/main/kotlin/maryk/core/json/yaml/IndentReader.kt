package maryk.core.json.yaml

import maryk.core.json.JsonToken
import maryk.core.json.TokenType

/** Reads indents on a new line until a char is found */
internal class IndentReader<out P>(
    yamlReader: YamlReaderImpl,
    parentReader: P
) : YamlCharWithParentReader<P>(yamlReader, parentReader),
    IsYamlCharWithIndentsReader,
    IsYamlCharWithChildrenReader
        where P : maryk.core.json.yaml.YamlCharReader,
              P : maryk.core.json.yaml.IsYamlCharWithChildrenReader,
              P : maryk.core.json.yaml.IsYamlCharWithIndentsReader {
    private var indentCounter = -1

    // Should not be called
    override fun <P> newIndentLevel(indentCount: Int, parentReader: P, tag: TokenType?): JsonToken
            where P : YamlCharReader,
                  P : IsYamlCharWithChildrenReader,
                  P : IsYamlCharWithIndentsReader =
        this.parentReader.newIndentLevel(indentCount, parentReader, tag)

    override fun continueIndentLevel(tag: TokenType?) =
        LineReader(
            this.yamlReader,
            this
        ).let {
            this.currentReader = it
            it.readUntilToken(tag)
        }

    override fun foundMap(isExplicitMap: Boolean, tag: TokenType?): JsonToken? =
        @Suppress("UNCHECKED_CAST")
        MapItemsReader(
            this.yamlReader,
            this,
            isExplicitMap
        ).let {
            this.currentReader = it
            it.readUntilToken(tag)
        }

    override fun isWithinMap() = false

    override fun endIndentLevel(
        indentCount: Int,
        tag: TokenType?,
        tokenToReturn: (() -> JsonToken)?
    ): JsonToken {
        this.parentReader.childIsDoneReading(true)

        tokenToReturn?.let {
            this.yamlReader.setUnclaimedIndenting(indentCount)
            return it()
        }

        @Suppress("UNCHECKED_CAST")
        (this.currentReader as P).let {
            return if (it.indentCount() == indentCount) {
                // found right level so continue
                this.yamlReader.setUnclaimedIndenting(null)
                if (it is IndentReader<*>) {
                    it.continueIndentLevel(null)
                } else {
                    it.readUntilToken()
                }
            } else {
                it.endIndentLevel(indentCount, tag, null)
            }
        }
    }

    override fun readUntilToken(tag: TokenType?): JsonToken {
        val currentIndentCount = this.yamlReader.skipEmptyLinesAndCommentsAndCountIndents()

        if (this.indentCounter == -1) {
            this.indentCounter = currentIndentCount

            if (this.parentReader is DocumentReader) {
                this.parentReader.setIndent(this.indentCounter)
            }
        }

        val parentIndentCount = this.parentReader.indentCount()
        return when(currentIndentCount) {
            parentIndentCount -> this.parentReader.continueIndentLevel(tag)
            in 0 until parentIndentCount -> {
                this.parentReader.childIsDoneReading(false)
                this.parentReader.endIndentLevel(currentIndentCount, tag, null)
            }
            else -> if (currentIndentCount == this.indentCounter){
                this.parentReader.newIndentLevel(currentIndentCount, this, tag)
            } else {
                throw InvalidYamlContent("Cannot have a new indent level which is lower than current")
            }
        }
    }

    override fun checkAndCreateFieldName(fieldName: String?, isPlainStringReader: Boolean) =
        this.parentReader.checkAndCreateFieldName(fieldName, isPlainStringReader)

    override fun indentCount() = this.indentCounter

    override fun indentCountForChildren() = this.indentCount()

    override fun childIsDoneReading(closeLineReader: Boolean) {
        this.currentReader = this
    }

    override fun handleReaderInterrupt(): JsonToken {
        this.parentReader.childIsDoneReading(false)
        return parentReader.handleReaderInterrupt()
    }
}
