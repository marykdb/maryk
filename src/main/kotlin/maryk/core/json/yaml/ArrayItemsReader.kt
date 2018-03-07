package maryk.core.json.yaml

import maryk.core.json.JsonToken
import maryk.core.json.TokenType

/** Reader for Array Items */
internal class ArrayItemsReader<out P>(
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
    override fun setTag(tag: TokenType) = this.parentReader.setTag(tag)

    private var isStarted = false

    override fun readUntilToken(): JsonToken {
        return if (!this.isStarted) {
            createLineReader(this)

            this.isStarted = true
            JsonToken.SimpleStartArray
        } else {
            IndentReader(
                yamlReader, this
            ).let {
                this.currentReader = it
                it.readUntilToken()
            }
        }
    }

    override fun foundMapKey(isExplicitMap: Boolean) = this.parentReader.foundMapKey(isExplicitMap)

    override fun <P> newIndentLevel(indentCount: Int, parentReader: P): JsonToken
            where P : YamlCharReader,
                  P : IsYamlCharWithChildrenReader,
                  P : IsYamlCharWithIndentsReader {
        this.createLineReader(parentReader)
        return this.currentReader.readUntilToken()
    }

    override fun continueIndentLevel(): JsonToken {
        if (this.lastChar != '-') {
            throwSequenceException()
        }
        read()
        if (!this.lastChar.isWhitespace()) {
            throwSequenceException()
        }
        read()
        return createLineReader(this).readUntilToken()
    }

    override fun indentCount() = this.parentReader.indentCountForChildren() + this.indentToAdd

    override fun indentCountForChildren() = this.indentCount() + 1

    override fun endIndentLevel(indentCount: Int, tokenToReturn: (() -> JsonToken)?): JsonToken {
        if (indentCount == this.indentCount()) {
            // this reader should handle the read
            this.currentReader = this
            return if (tokenToReturn != null) {
                this.yamlReader.hasUnclaimedIndenting(indentCount)
                tokenToReturn()
            } else {
                this.yamlReader.hasUnclaimedIndenting(null)
                this.continueIndentLevel()
            }
        }

        return if (indentToAdd > 0) {
            this.yamlReader.hasUnclaimedIndenting(indentCount)
            this.parentReader.childIsDoneReading()
            JsonToken.EndArray
        } else {
            this.parentReader.endIndentLevel(indentCount) { JsonToken.EndArray }
        }
    }

    override fun childIsDoneReading() {
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