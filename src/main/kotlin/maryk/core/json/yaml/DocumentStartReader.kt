package maryk.core.json.yaml

import maryk.core.json.JsonToken

internal class DocumentStartReader(
    yamlReader: YamlReaderImpl
): YamlCharReader(yamlReader),
    IsYamlCharWithChildrenReader,
    IsYamlCharWithIndentsReader
{
    private var mapKeyFound: Boolean = false

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
                    yamlReader = this.yamlReader
                ).let {
                    this.currentReader = it
                    it.readUntilToken()
                }
            }
        }
    }

    override fun foundMapKey(isExplicitMap: Boolean): JsonToken? =
        if (!this.mapKeyFound) {
            this.mapKeyFound = true
            JsonToken.StartObject
        } else {
            null
        }

    override fun <P> newIndentLevel(parentReader: P)
            where P : YamlCharReader,
                  P : IsYamlCharWithChildrenReader,
                  P : IsYamlCharWithIndentsReader =
        LineReader(
            parentReader = parentReader,
            yamlReader = this.yamlReader
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
        if (this.mapKeyFound) {
            this.mapKeyFound = false
            return JsonToken.EndObject
        }

        return JsonToken.EndJSON
    }

    private fun plainStringReader(char: String): JsonToken {
        val lineReader = LineReader(this.yamlReader, this)

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
}