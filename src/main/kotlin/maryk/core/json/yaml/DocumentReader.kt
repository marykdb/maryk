package maryk.core.json.yaml

import maryk.core.json.JsonToken

/** Read a complete yaml document until end stream or "..." */
internal class DocumentReader(
    yamlReader: YamlReaderImpl
): YamlCharReader(yamlReader),
    IsYamlCharWithChildrenReader,
    IsYamlCharWithIndentsReader
{
    private var finishedWithDirectives: Boolean? = null
    private var mapKeyFound: Boolean = false

    private var contentWasFound = false

    override fun readUntilToken(): JsonToken {
        if(this.lastChar == '\u0000') {
            this.read()
        }

        return when(this.lastChar) {
            '%' -> {
                if (this.finishedWithDirectives == true) {
                    throw InvalidYamlContent("Cannot start another directives block")
                }

                this.finishedWithDirectives = false
                this.read()

                DirectiveReader(this.yamlReader, this).let {
                    this.currentReader = it
                    it.readUntilToken()
                }
            }
            '-' -> {
                this.read()

                when(this.lastChar) {
                    '-' -> {
                        this.read()
                        when(this.lastChar) {
                            '-' -> {
                                read()
                                return if (this.contentWasFound) {
                                    JsonToken.StartDocument
                                } else {
                                    // First found document open before content
                                    this.finishedWithDirectives = true
                                    this.readUntilToken()
                                }
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
            '.' -> {
                this.read()

                when(this.lastChar) {
                    '.' -> {
                        this.read()
                        when(this.lastChar) {
                            '.' -> {
                                read()
                                JsonToken.EndDocument
                            }
                            else -> plainStringReader("..")
                        }
                    }
                    else -> plainStringReader(".")
                }
            }
            '#' -> {
                CommentReader(this.yamlReader, this).let {
                    this.currentReader = it
                    it.readUntilToken()
                }
            }
            '\n' -> {
                read()
                this.readUntilToken()
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
                if (this.finishedWithDirectives == false) {
                    throw InvalidYamlContent("Directives has to end with an start document --- separator")
                }
                LineReader(
                    parentReader = this,
                    yamlReader = this.yamlReader
                ).let {
                    this.currentReader = it
                    it.readUntilToken()
                }
            }
        }.also {
            this.contentWasFound = true
        }
    }

    override fun foundMapKey(isExplicitMap: Boolean): JsonToken? =
        if (!this.mapKeyFound) {
            this.mapKeyFound = true
            JsonToken.StartObject
        } else {
            null
        }

    override fun <P> newIndentLevel(indentCount: Int, parentReader: P)
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

    override fun endIndentLevel(indentCount: Int, tokenToReturn: (() -> JsonToken)?) =
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

        return JsonToken.EndDocument
    }

    private fun plainStringReader(char: String): JsonToken {
        val lineReader = LineReader(this.yamlReader, this)

        return PlainStringReader(
            this.yamlReader,
            lineReader,
            char
        ) {
            JsonToken.Value(it)
        }.let {
            this.currentReader = it
            it.readUntilToken()
        }
    }
}