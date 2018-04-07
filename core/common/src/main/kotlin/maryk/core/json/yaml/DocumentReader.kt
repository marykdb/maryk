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

    override fun readUntilToken(extraIndent: Int, tag: TokenType?): JsonToken {
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

                this.directiveReader {
                    this.readUntilToken(0, tag)
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
                                    this.readUntilToken(0)
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
                            it.readUntilToken(0, tag)
                        }
                    }
                    else -> plainStringReader("-")
                }
            }
            '.' -> {
                try {
                    this.read()
                } catch(e: ExceptionWhileReadingJson) {
                    return plainStringReader("")
                }

                when(this.lastChar) {
                    '.' -> {
                        try {
                            this.read()
                        } catch(e: ExceptionWhileReadingJson) {
                            return plainStringReader(".")
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
                this.commentReader {
                    this.readUntilToken(0, tag)
                }
            }
            '\n' -> {
                read()
                this.readUntilToken(0)
            }
            ' ' -> {
                IndentReader(
                    parentReader = this,
                    yamlReader = this.yamlReader
                ).let {
                    this.currentReader = it
                    it.readUntilToken(0)
                }
            } else -> {
                if (this.finishedWithDirectives == false) {
                    throw InvalidYamlContent("Directives has to end with an start document --- separator")
                }
                return this.newLineReader(true, tag, 0) { value, isPlainString, tagg ->
                    createYamlValueToken(value, tagg, isPlainString)
                }
            }
        }.also {
            this.contentWasFound = true
        }
    }

    override fun foundMap(tag: TokenType?, startedAtIndent: Int): JsonToken {
        return MapItemsReader(
            this.yamlReader,
            this,
            indentToAdd = startedAtIndent
        ).let {
            this.currentReader = it
            it.readUntilToken(0, tag)
        }
    }

    override fun checkAndCreateFieldName(fieldName: String?, isPlainStringReader: Boolean) =
        throw InvalidYamlContent("FieldNames are only allowed within maps")

    override fun isWithinMap() = false

    override fun continueIndentLevel(extraIndent: Int, tag: TokenType?): JsonToken {
        return if (this.indentCount == 0) {
            readUntilToken(extraIndent, tag)
        } else {
            this.lineReader(this, true)
                .readUntilToken(0, tag)
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

        @Suppress("UNCHECKED_CAST")
        return lineReader.plainStringReader(startWith, null, PlainStyleMode.NORMAL, 0) { value, _, _ ->
            JsonToken.Value(value, ValueType.String)
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
