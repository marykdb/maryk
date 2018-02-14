package maryk.core.json.yaml

import maryk.core.json.InvalidJsonContent
import maryk.core.json.JsonToken

/** Reader for Array Items */
internal class ArrayItemsReader<out P>(
    yamlReader: YamlReader,
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

    override fun readUntilToken(): JsonToken {
        return if (!this.isStarted) {
            createLineReader()

            this.isStarted = true
            JsonToken.StartArray
        } else {
            IndentReader(
                yamlReader, this
            ).let {
                this.currentReader = it
                it.readUntilToken()
            }
        }
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
        return createLineReader().readUntilToken()
    }

    override fun indentCount() = this.parentReader.indentCount() + this.indentToAdd

    override fun endIndentLevel(indentCount: Int, tokenToReturn: JsonToken?): JsonToken {
        if (indentToAdd > 0) {
            this.yamlReader.hasUnclaimedIndenting(indentCount)
            this.parentReader.childIsDoneReading()
            return JsonToken.EndArray
        } else {
            return this.parentReader.endIndentLevel(indentCount, JsonToken.EndArray)
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
        throw InvalidJsonContent("Sequence was started on this indentation level, this is not an Sequence entry")
    }

    private fun createLineReader() = LineReader(
        yamlReader = yamlReader,
        parentReader = this,
        jsonTokenCreator = { JsonToken.ArrayValue(it) }
    ).apply {
        this.currentReader = this
    }
}