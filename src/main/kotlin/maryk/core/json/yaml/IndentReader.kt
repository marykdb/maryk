package maryk.core.json.yaml

import maryk.core.json.JsonToken

private val lineBreakChars = arrayOf('\n', '\r')

/** Reads indents on a new line until a char is found */
internal class IndentReader<out P>(
    yamlReader: YamlReader,
    parentReader: P
) : YamlCharWithParentReader<P>(yamlReader, parentReader),
    IsYamlCharWithIndentsReader,
    IsYamlCharWithChildrenReader
        where P : maryk.core.json.yaml.YamlCharReader,
              P : maryk.core.json.yaml.IsYamlCharWithChildrenReader,
              P : maryk.core.json.yaml.IsYamlCharWithIndentsReader
{
    private var indentCounter = 0


    override fun <P> newIndentLevel(parentReader: P): JsonToken
            where P : YamlCharReader,
                  P : IsYamlCharWithChildrenReader,
                  P : IsYamlCharWithIndentsReader {
        TODO("not implemented")
    }

    override fun continueIndentLevel(): JsonToken {
        TODO("not implemented")
    }

    override fun endIndentLevel(indentCount: Int, tokenToReturn: JsonToken?): JsonToken {
        this.yamlReader.hasUnclaimedIndenting(indentCount)
        this.parentReader.childIsDoneReading()
        return tokenToReturn ?: this.currentReader.readUntilToken()
    }

    override fun readUntilToken(): JsonToken {
        while(this.lastChar.isWhitespace()) {
            if (this.lastChar in lineBreakChars) {
                indentCounter = 0
            } else {
                this.indentCounter++
            }
            read()
        }

        val parentIndentCount = this.parentReader.indentCount()
        return when(this.indentCounter) {
            parentIndentCount -> this.parentReader.continueIndentLevel()
            in 0 until parentIndentCount -> this.parentReader.endIndentLevel(this.indentCounter)
            else -> this.parentReader.newIndentLevel(this)
        }
    }

    override fun indentCount() = indentCounter

    override fun indentCountForChildren() = this.indentCount()

    override fun childIsDoneReading() {
        this.currentReader = this
    }

    override fun handleReaderInterrupt() = parentReader.handleReaderInterrupt()
}