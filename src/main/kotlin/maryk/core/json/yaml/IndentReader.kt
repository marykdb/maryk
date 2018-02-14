package maryk.core.json.yaml

import maryk.core.json.JsonToken

private val lineBreakChars = arrayOf('\n', '\r')

/** Reads indents on a new line until a char is found */
internal class IndentReader(
    yamlReader: YamlReader,
    parentReader: YamlCharWithChildrenReader
) : YamlCharWithChildrenReader(yamlReader, parentReader) {
    var indentCounter = 0

    override fun continueIndentLevel(): JsonToken {
        TODO("not implemented")
    }

    override fun endIndentLevel(indentCount: Int, tokenToReturn: JsonToken?): JsonToken {
        this.yamlReader.hasUnclaimedIndenting(indentCount)
        this.parentReader!!.childIsDoneReading()
        return tokenToReturn!!
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

        return when(this.indentCounter) {
            this.parentReader!!.indentCount() -> this.parentReader.continueIndentLevel()
            in 0 until this.parentReader.indentCount() -> this.parentReader.endIndentLevel(this.indentCounter)
            else -> {
                this.yamlReader.currentReader = LineReader(
                    parentReader = this,
                    yamlReader = this.yamlReader,
                    jsonTokenCreator = { JsonToken.ObjectValue(it) }
                )
                this.yamlReader.currentReader.readUntilToken()
            }
        }
    }

    override fun indentCount() = indentCounter

    override fun childIsDoneReading() {
        this.currentReader = this
    }

    override fun handleReaderInterrupt(): JsonToken {
        return JsonToken.EndJSON
    }
}