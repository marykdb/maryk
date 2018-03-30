package maryk.core.json.yaml

import maryk.core.json.ExceptionWhileReadingJson
import maryk.core.json.JsonToken
import maryk.core.json.TokenType
import maryk.core.json.ValueType

/** Read single or multiple yaml documents until end of stream or "..." */
internal class DocumentReader(
    yamlReader: YamlReaderImpl
): YamlCharReader(yamlReader),
    IsYamlCharWithChildrenReader,
    IsYamlCharWithIndentsReader
{
    private var finishedWithDirectives: Boolean? = null
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
                try {
                    this.read()
                } catch(e: ExceptionWhileReadingJson) {
                    return plainStringReader("")
                }

                when(this.lastChar) {
                    '-' -> {
                        try {
                            this.read()
                        } catch(e: ExceptionWhileReadingJson) {
                            plainStringReader("-")
                        }
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
                    ' ', '\n', '\r' -> {
                        checkAlreadyOnIndent()

                        SequenceItemsReader(
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
                try {
                    this.read()
                } catch(e: ExceptionWhileReadingJson) {
                    plainStringReader("")
                }

                when(this.lastChar) {
                    '.' -> {
                        try {
                            this.read()
                        } catch(e: ExceptionWhileReadingJson) {
                            plainStringReader(".")
                        }
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
                return this.lineReader(this, true)
                    .readUntilToken(tag)
            }
        }.also {
            this.contentWasFound = true
        }
    }

    override fun foundMap(isExplicitMap: Boolean, tag: TokenType?): JsonToken {
        @Suppress("UNCHECKED_CAST")
        return MapItemsReader(
            this.yamlReader,
            this,
            isExplicitMap
        ).let {
            this.currentReader = it
            it.readUntilToken(tag)
        }
    }

    override fun checkAndCreateFieldName(fieldName: String?, isPlainStringReader: Boolean) =
        throw InvalidYamlContent("FieldNames are only allowed within maps")

    override fun isWithinMap() = false

    override fun <P> newIndentLevel(indentCount: Int, parentReader: P, tag: TokenType?): JsonToken
            where P : YamlCharReader,
                  P : IsYamlCharWithChildrenReader,
                  P : IsYamlCharWithIndentsReader {
        @Suppress("UNCHECKED_CAST")
        return (this as P).lineReader(parentReader, true).readUntilToken(tag)
    }

    override fun continueIndentLevel(tag: TokenType?): JsonToken {
        if (this.indentCount == 0) {
            return readUntilToken(tag)
        } else {
            return newIndentLevel(this.indentCount, this, tag)
        }
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
        return JsonToken.EndDocument
    }

    private fun plainStringReader(startWith: String): JsonToken {
        checkAlreadyOnIndent()

        val lineReader = LineReader(this.yamlReader, this, true)

        return PlainStringReader(
            this.yamlReader,
            lineReader,
            startWith
        ) {
            JsonToken.Value(it, ValueType.String)
        }.let {
            this.currentReader = it
            it.readUntilToken()
        }
    }

    /** Checks if indentation was not reset and thus reading on a lower indent than started */
    private fun checkAlreadyOnIndent() {
        if (this.indentCount == -1) {
            throw InvalidYamlContent("Document should not have a lower indent than started")
        }
    }

    /** Set [indentCount] for document so it can check if next levels don't start lower */
    internal fun setIndent(indentCount: Int) {
        this.indentCount = indentCount
    }
}
