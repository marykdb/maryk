package maryk.core.json.yaml

import maryk.core.json.JsonToken

internal class DocumentStartReader(
    yamlReader: YamlReader
): YamlCharWithChildrenReader(yamlReader) {

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
                            else -> {
                                TODO("start string")
                            }
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
                    else -> {
                        TODO("start string")
                    }
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

    override fun continueIndentLevel() = readUntilToken()

    override fun indentCount() = 0

    override fun childIsDoneReading() {
        this.currentReader = this
    }

    override fun endIndentLevel() = JsonToken.EndJSON

    override fun handleReaderInterrupt() = EndReader(
        this.yamlReader
    ).apply {
        this.currentReader = this
    }.readUntilToken()
}