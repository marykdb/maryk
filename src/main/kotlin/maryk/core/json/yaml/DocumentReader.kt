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

    private var tag: TokenType? = null

    private var indentCount: Int = 0

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
                            parentReader = this,
                            startTag = this.tag
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
                this.lineReader(this)
            }
        }.also {
            this.contentWasFound = true
        }
    }

    override fun foundMap(isExplicitMap: Boolean): JsonToken? =
        if (!this.mapKeyFound) {
            this.mapKeyFound = true
            this.tag?.let {
                this.tag = null
                (it as? MapType)?.let {
                    JsonToken.StartObject(it)
                } ?: throw InvalidYamlContent("Cannot use non map tags on maps")
            } ?: JsonToken.SimpleStartObject
        } else {
            null
        }

    override fun checkDuplicateFieldName(fieldName: String?) {
        if(!this.fieldNames.contains(fieldName)) {
            this.fieldNames += fieldName
        } else {
            throw InvalidYamlContent("Duplicate field name $fieldName in flow map")
        }
    }

    override fun isWithinMap() = this.mapKeyFound

    override fun <P> newIndentLevel(indentCount: Int, parentReader: P, tag: TokenType?): JsonToken
            where P : YamlCharReader,
                  P : IsYamlCharWithChildrenReader,
                  P : IsYamlCharWithIndentsReader {
        this.tag = tag
        return this.lineReader(parentReader, tag)
    }

    override fun continueIndentLevel(tag: TokenType?): JsonToken {
        this.tag = tag
        return readUntilToken()
    }

    override fun endIndentLevel(indentCount: Int, tokenToReturn: (() -> JsonToken)?): JsonToken {
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
            yamlReader = this.yamlReader,
            startTag = tag
        ).let {
            this.currentReader = it
            it.readUntilToken()
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
