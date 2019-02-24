package maryk.yaml

import maryk.json.ExceptionWhileReadingJson
import maryk.json.JsonToken
import maryk.json.TokenType
import maryk.json.ValueType

/** Read single or multiple yaml documents until end of stream or "..." */
internal class DocumentReader(
    yamlReader: YamlReaderImpl
) : YamlCharReader(yamlReader),
    IsYamlCharWithIndentsReader {
    private var finishedWithDirectives: Boolean? = null
    private var firstDocumentContentWasFound = false
    private var contentWasFound = false
    private var indentCount: Int = 0

    override fun readUntilToken(extraIndent: Int, tag: TokenType?): JsonToken {
        if (this.lastChar == '\u0000') {
            this.read()
        }

        return when (this.lastChar) {
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
                } catch (e: ExceptionWhileReadingJson) {
                    return plainStringReader("")
                }

                when (this.lastChar) {
                    '-' -> {
                        try {
                            this.read()
                        } catch (e: ExceptionWhileReadingJson) {
                            plainStringReader("-")
                        }
                        when (this.lastChar) {
                            '-' -> {
                                read()
                                return if (this.firstDocumentContentWasFound) {
                                    this.contentWasFound = false
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
                } catch (e: ExceptionWhileReadingJson) {
                    return plainStringReader("")
                }

                when (this.lastChar) {
                    '.' -> {
                        try {
                            this.read()
                        } catch (e: ExceptionWhileReadingJson) {
                            return plainStringReader(".")
                        }
                        when (this.lastChar) {
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
                val indentCount = this.yamlReader.skipEmptyLinesAndCommentsAndCountIndents()

                if (this.indentCount <= 0 && !this.contentWasFound) {
                    this.indentCount = indentCount
                }
                if (indentCount != this.indentCount) {
                    throw InvalidYamlContent("Cannot have a new indent level which is lower than current")
                } else {
                    this.selectReaderAndRead(true, tag, 0) { value, isPlainString, tagg, _ ->
                        createYamlValueToken(value, tagg, isPlainString)
                    }
                }
            }
            else -> {
                if (this.finishedWithDirectives == false) {
                    throw InvalidYamlContent("Directives has to end with an start document --- separator")
                }
                this.checkAlreadyOnIndent()

                return this.selectReaderAndRead(true, tag, 0) { value, isPlainString, tagg, _ ->
                    createYamlValueToken(value, tagg, isPlainString)
                }
            }
        }.also {
            this.firstDocumentContentWasFound = true
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
        throw Exception("FieldNames are only allowed within maps")

    override fun continueIndentLevel(extraIndent: Int, tag: TokenType?): JsonToken {
        return if (this.indentCount == 0) {
            readUntilToken(extraIndent, tag)
        } else {
            this.selectReaderAndRead(true, tag, extraIndent) { value, isPlainString, tagg, _ ->
                createYamlValueToken(value, tagg, isPlainString)
            }
        }
    }

    override fun endIndentLevel(
        indentCount: Int,
        tag: TokenType?,
        tokenToReturn: (() -> JsonToken)?
    ): JsonToken {
        tokenToReturn?.let {
            this.yamlReader.setUnclaimedIndenting(indentCount)
            return it()
        }
        if (indentCount < this.indentCount && indentCount > 0) {
            throw InvalidYamlContent("Document should not have a lower indent than started")
        }
        return readUntilToken(indentCount, tag)
    }

    override fun indentCount() = this.indentCount

    override fun handleReaderInterrupt(): JsonToken {
        return JsonToken.EndDocument
    }

    private fun plainStringReader(startWith: String): JsonToken {
        checkAlreadyOnIndent()

        @Suppress("UNCHECKED_CAST")
        return this.plainStringReader(startWith, null, PlainStyleMode.NORMAL, 0) { value, _, _, _ ->
            JsonToken.Value(value, ValueType.String)
        }
    }

    /** Checks if indentation was not reset and thus reading on a lower indent than started */
    private fun checkAlreadyOnIndent() {
        if (this.indentCount > 0 && this.contentWasFound) {
            throw InvalidYamlContent("Document should not have a lower indent than started")
        }
    }
}
