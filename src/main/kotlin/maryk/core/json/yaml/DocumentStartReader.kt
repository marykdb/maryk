package maryk.core.json.yaml

import maryk.core.json.JsonToken

internal class DocumentStartReader(
    yamlReader: YamlReader
): YamlCharReader(yamlReader),
    IsYamlCharWithChildrenReader,
    IsYamlCharWithIndentsReader
{
    override fun readUntilToken(): JsonToken {
        this.read()
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

    private fun plainStringReader(char: String): JsonToken {
        return PlainStringReader(
            this.yamlReader,
            this,
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

    override fun endIndentLevel(indentCount: Int, tokenToReturn: JsonToken?) = JsonToken.EndJSON

    override fun indentCount() = 0

    override fun indentCountForChildren() = this.indentCount()

    override fun childIsDoneReading() {
        this.currentReader = this
    }

    override fun handleReaderInterrupt() = EndReader(
        this.yamlReader
    ).apply {
        this.currentReader = this
    }.readUntilToken()
}