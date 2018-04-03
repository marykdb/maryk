package maryk.core.json.yaml

import maryk.core.json.JsonToken
import maryk.core.json.TokenType

/**
 * Reads indents on a new line until a char is found and then determines to which reader to continue.
 * It will either start a new indent level on parent, continue on current parent or close the parent
 */
internal class IndentReader<out P>(
    yamlReader: YamlReaderImpl,
    parentReader: P
) : YamlCharWithParentAndIndentReader<P>(yamlReader, parentReader),
    IsYamlCharWithIndentsReader,
    IsYamlCharWithChildrenReader
        where P : maryk.core.json.yaml.YamlCharReader,
              P : maryk.core.json.yaml.IsYamlCharWithChildrenReader,
              P : maryk.core.json.yaml.IsYamlCharWithIndentsReader {
    private var indentCounter = -1

    override fun continueIndentLevel(extraIndent: Int, tag: TokenType?) =
        this.lineReader(this, true)
            .readUntilToken(extraIndent, tag)

    override fun foundMap(tag: TokenType?, startedAtIndent: Int): JsonToken? =
        @Suppress("UNCHECKED_CAST")
        MapItemsReader(
            this.yamlReader,
            this,
            indentToAdd = startedAtIndent
        ).let {
            this.currentReader = it
            it.readUntilToken(0, tag)
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
                    it.continueIndentLevel(0, null)
                } else {
                    it.readUntilToken(extraIndent = 0)
                }
            } else {
                it.endIndentLevel(indentCount, tag, null)
            }
        }
    }

    override fun readUntilToken(extraIndent: Int, tag: TokenType?): JsonToken {
        val currentIndentCount = this.yamlReader.skipEmptyLinesAndCommentsAndCountIndents()

        if (this.indentCounter == -1) {
            this.indentCounter = currentIndentCount

            if (this.parentReader is DocumentReader) {
                this.parentReader.setIndent(this.indentCounter)
            }
        }

        val parentIndentCount = this.parentReader.indentCount()
        return when(currentIndentCount) {
            parentIndentCount -> this.parentReader.continueIndentLevel(extraIndent, tag)
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

    override fun indentCount() = this.indentCounter

    override fun indentCountForChildren() = this.indentCount()
}
