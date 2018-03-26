package maryk.core.json.yaml

import maryk.core.json.JsonToken
import maryk.core.json.MapType
import maryk.core.json.TokenType
import maryk.core.json.ValueType

/** Read a complete yaml document until end stream or "..." */
internal class DocumentReader(
    yamlReader: YamlReaderImpl
): YamlCharReader(yamlReader),
    IsYamlCharWithChildrenReader,
    IsYamlCharWithIndentsReader
{
    private var finishedWithDirectives: Boolean? = null
    private var mapKeyFound: Boolean = false
    private val fieldNames = mutableListOf<String?>()

    private var contentWasFound = false

    private var indentCount: Int = 0

    override fun readUntilToken(tag: TokenType?): JsonToken {
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
                                    this.indentCount = 0
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
                        checkAlreadyOnIndent()

                        ArrayItemsReader(
                            yamlReader = this.yamlReader,
                            parentReader = this
                        ).let {
                            this.currentReader = it
                            it.readUntilToken(tag)
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
                this.lineReader(this)
            }
        }.also {
            this.contentWasFound = true
        }
    }

    override fun foundMap(isExplicitMap: Boolean, tag: TokenType?): JsonToken? =
        if (!this.mapKeyFound) {
            this.mapKeyFound = true
            tag?.let {
                (it as? MapType)?.let {
                    JsonToken.StartObject(it)
                } ?: throw InvalidYamlContent("Cannot use non map tags on maps")
            } ?: JsonToken.SimpleStartObject
        } else {
            null
        }

    override fun checkAndCreateFieldName(fieldName: String?, isPlainStringReader: Boolean) =
        checkAndCreateFieldName(this.fieldNames, fieldName, isPlainStringReader)

    override fun isWithinMap() = this.mapKeyFound

    override fun <P> newIndentLevel(indentCount: Int, parentReader: P, tag: TokenType?): JsonToken
            where P : YamlCharReader,
                  P : IsYamlCharWithChildrenReader,
                  P : IsYamlCharWithIndentsReader {
        return this.lineReader(parentReader, tag)
    }

    override fun continueIndentLevel(tag: TokenType?): JsonToken {
        return readUntilToken(tag)
    }

    override fun endIndentLevel(
        indentCount: Int,
        tag: TokenType?,
        tokenToReturn: (() -> JsonToken)?
    ): JsonToken {
        if (indentCount == 0
            && tokenToReturn != null
            && (this.lastChar == '-' || this.lastChar == '.')
        ) {
            this.indentCount = -1 // fail indents
            return tokenToReturn()
        }
        throw InvalidYamlContent("Document should not have a lower indent than started")
    }

    override fun indentCount() = this.indentCount

    override fun indentCountForChildren() = this.indentCount()

    override fun childIsDoneReading(closeLineReader: Boolean) {
        this.currentReader = this
    }

    override fun handleReaderInterrupt(): JsonToken {
        if (this.mapKeyFound) {
            this.mapKeyFound = false
            return JsonToken.EndObject
        }

        return JsonToken.EndDocument
    }

    private fun <P> lineReader(parentReader: P, tag: TokenType? = null): JsonToken
            where P : maryk.core.json.yaml.YamlCharReader,
                  P : maryk.core.json.yaml.IsYamlCharWithChildrenReader,
                  P : maryk.core.json.yaml.IsYamlCharWithIndentsReader {
        return LineReader(
            parentReader = parentReader,
            yamlReader = this.yamlReader
        ).let {
            this.currentReader = it
            it.readUntilToken(tag)
        }
    }

    private fun plainStringReader(char: String): JsonToken {
        checkAlreadyOnIndent()

        val lineReader = LineReader(this.yamlReader, this)

        return PlainStringReader(
            this.yamlReader,
            lineReader,
            char
        ) {
            JsonToken.Value(it, ValueType.String)
        }.let {
            this.currentReader = it
            it.readUntilToken()
        }
    }

    private fun checkAlreadyOnIndent() {
        if (this.indentCount == -1) {
            throw InvalidYamlContent("Document should not have a lower indent than started")
        }
    }

    internal fun setIndent(indentCount: Int) {
        this.indentCount = indentCount
    }
}
