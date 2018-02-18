package maryk.core.json.yaml

import maryk.core.json.JsonToken

internal class DocumentStartReader(
    yamlReader: YamlReader
): YamlCharReader(yamlReader),
    IsYamlCharWithChildrenReader,
    IsYamlCharWithIndentsReader
{
    private var indentType: IndentObjectType = IndentObjectType.UNKNOWN

    override fun readUntilToken(): JsonToken {
        if(this.lastChar == '\u0000') {
            this.read()
        }

        return when(this.lastChar) {
            '-' -> {
                this.read()

                when(this.lastChar) {
                    '-' -> {
                        this.read()
                        when(this.lastChar) {
                            '-' -> {
                                TODO("document started")
                            }
                            else -> plainStringReader("--")
                        }
                    }
                    ' ' -> {
                        ArrayItemsReader(
                            yamlReader = this.yamlReader,
                            parentReader = this
                        ).let {
                            this.currentReader = it
                            it.readUntilToken()
                        }
                    }
                    else -> plainStringReader("-")
                }
            }
            ' ' -> {
                IndentReader(
                    parentReader = this,
                    yamlReader = this.yamlReader
                ).let {
                    this.currentReader = it
                    it.readUntilToken()
                }
            } else -> {
                LineReader(
                    parentReader = this,
                    yamlReader = this.yamlReader,
                    jsonTokenCreator = { JsonToken.ObjectValue(it) }
                ).let {
                    this.currentReader = it
                    it.readUntilToken()
                }
            }
        }
    }

    override fun foundIndentType(type: IndentObjectType): JsonToken? =
        if (this.indentType == IndentObjectType.UNKNOWN) {
            this.indentType = IndentObjectType.OBJECT
            JsonToken.StartObject
        } else {
            null
        }

    private fun plainStringReader(char: String): JsonToken {
        val lineReader = LineReader(this.yamlReader, this) {
            JsonToken.ObjectValue(it)
        }

        return PlainStringReader(
            this.yamlReader,
            lineReader,
            char
        ) {
            JsonToken.ObjectValue(it)
        }.let {
            this.currentReader = it
            it.readUntilToken()
        }
    }

    override fun <P> newIndentLevel(parentReader: P)
            where P : YamlCharReader,
                  P : IsYamlCharWithChildrenReader,
                  P : IsYamlCharWithIndentsReader = LineReader(
        parentReader = parentReader,
        yamlReader = this.yamlReader,
        jsonTokenCreator = { JsonToken.ObjectValue(it) }
    ).let {
        this.currentReader = it
        it.readUntilToken()
    }

    override fun continueIndentLevel() = readUntilToken()

    override fun endIndentLevel(indentCount: Int, tokenToReturn: JsonToken?) =
        handleReaderInterrupt()

    override fun indentCount() = 0

    override fun indentCountForChildren() = this.indentCount()

    override fun childIsDoneReading() {
        this.currentReader = this
    }

    override fun handleReaderInterrupt(): JsonToken {
        if (this.indentType == IndentObjectType.OBJECT) {
            this.indentType = IndentObjectType.UNKNOWN
            return JsonToken.EndObject
        }

        return EndReader(
            this.yamlReader
        ).apply {
            this.currentReader = this
        }.readUntilToken()
    }
}