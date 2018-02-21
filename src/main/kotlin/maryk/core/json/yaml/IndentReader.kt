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
    private var indentCounter = -1
    private var indentType: IndentObjectType = IndentObjectType.UNKNOWN

    override fun <P> newIndentLevel(parentReader: P): JsonToken
            where P : YamlCharReader,
                  P : IsYamlCharWithChildrenReader,
                  P : IsYamlCharWithIndentsReader {
        TODO("not implemented")
    }

    override fun continueIndentLevel() =
        LineReader(this.yamlReader, this).let {
            this.currentReader = it
            it.readUntilToken()
        }

    override fun foundIndentType(type: IndentObjectType): JsonToken? =
        if (this.indentType == IndentObjectType.UNKNOWN) {
            this.indentType = IndentObjectType.OBJECT
            JsonToken.StartObject
        } else {
            null
        }

    override fun endIndentLevel(indentCount: Int, tokenToReturn: JsonToken?): JsonToken {
        this.yamlReader.hasUnclaimedIndenting(indentCount)

        if (this.indentType == IndentObjectType.OBJECT) {
            this.indentType = IndentObjectType.UNKNOWN
            return JsonToken.EndObject
        }

        this.parentReader.childIsDoneReading()
        return tokenToReturn ?: this.currentReader.readUntilToken()
    }

    override fun readUntilToken(): JsonToken {
        var currentIndentCount = 0
        while(this.lastChar.isWhitespace()) {
            if (this.lastChar in lineBreakChars) {
                currentIndentCount = 0
            } else {
                currentIndentCount++
            }
            read()
        }

        if (this.indentCounter == -1) {
            this.indentCounter = currentIndentCount
        }

        val parentIndentCount = this.parentReader.indentCount()
        return when(currentIndentCount) {
            parentIndentCount -> this.parentReader.continueIndentLevel()
            in 0 until parentIndentCount -> {
                if (this.indentType == IndentObjectType.OBJECT) {
                    this.indentType = IndentObjectType.UNKNOWN
                    this.parentReader.childIsDoneReading()
                    return this.parentReader.endIndentLevel(
                        currentIndentCount,
                        tokenToReturn = JsonToken.EndObject
                    )
                }

                this.parentReader.endIndentLevel(currentIndentCount)
            }
            else -> this.parentReader.newIndentLevel(this)
        }
    }

    override fun indentCount() = this.indentCounter

    override fun indentCountForChildren() = this.indentCount()

    override fun childIsDoneReading() {
        this.currentReader = this
    }

    override fun handleReaderInterrupt(): JsonToken {
        if (this.indentType == IndentObjectType.OBJECT) {
            this.indentType = IndentObjectType.UNKNOWN
            return JsonToken.EndObject
        }
        return parentReader.handleReaderInterrupt()
    }
}