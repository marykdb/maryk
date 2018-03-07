package maryk.core.json.yaml

import maryk.core.extensions.isLineBreak
import maryk.core.extensions.isSpacing
import maryk.core.json.JsonToken

/** Reads Lines with actual non whitespace chars */
internal class LineReader<out P>(
    yamlReader: YamlReaderImpl,
    parentReader: P,
    private var indentToAdd: Int = 0
) : YamlCharWithParentReader<P>(yamlReader, parentReader),
    IsYamlCharWithIndentsReader,
    IsYamlCharWithChildrenReader
        where P : YamlCharReader,
              P : IsYamlCharWithChildrenReader,
              P : IsYamlCharWithIndentsReader
{
    private var hasCompletedValueReading = false
    private var isExplicitMap = false
    private var mapKeyFound = false
    private var mapValueFound = false

    private var hasFoundFieldName: JsonToken.FieldName? = null

    override fun readUntilToken(): JsonToken {
        if (this.hasFoundFieldName != null) {
            return this.hasFoundFieldName!!.also {
                this.hasFoundFieldName = null
            }
        }

        val indents = skipWhiteSpace()

        return when(this.lastChar) {
            '\n', '\r' -> {
                read()
                if (this.hasCompletedValueReading) {
                    this.parentReader.childIsDoneReading()
                }
                @Suppress("UNCHECKED_CAST")
                IndentReader(this.yamlReader, this.currentReader as P).let {
                    this.currentReader = it
                    it.readUntilToken()
                }
            }
            '\'' -> {
                read()
                StringInSingleQuoteReader(this.yamlReader, this) {
                    this.jsonTokenCreator(it)
                }.let {
                    this.currentReader = it
                    it.readUntilToken()
                }
            }
            '\"' -> {
                read()
                StringInDoubleQuoteReader(this.yamlReader, this) {
                    this.jsonTokenCreator(it)
                }.let {
                    this.currentReader = it
                    it.readUntilToken()
                }
            }
            '[' -> {
                read()
                FlowSequenceReader(
                    yamlReader = this.yamlReader,
                    parentReader = this
                ).let {
                    this.currentReader = it
                    it.readUntilToken()
                }
            }
            '{' -> {
                read()
                FlowMapItemsReader(
                    yamlReader = this.yamlReader,
                    parentReader = this
                ).let {
                    this.currentReader = it
                    it.readUntilToken()
                }
            }
            ',' -> {
                throw InvalidYamlContent("Invalid char $lastChar at this position")
            }
            '|' -> {
                read()
                return LiteralStringReader(
                    this.yamlReader,
                    this
                ) {
                    this.jsonTokenCreator(it)
                }.let {
                    this.currentReader = it
                    it.readUntilToken()
                }
            }
            '>' -> {
                read()
                return FoldedStringReader(
                    this.yamlReader,
                    this
                ) {
                    this.jsonTokenCreator(it)
                }.let {
                    this.currentReader = it
                    it.readUntilToken()
                }
            }
            '!' -> {
                TagReader(this.yamlReader, this).let {
                    this.currentReader = it
                    it.readUntilToken()
                }
            }
            '@', '`' -> {
                throw InvalidYamlContent("Reserved indicators for future use and not supported by this reader")
            }
            '%' -> {
                throw InvalidYamlContent("Directive % indicator not allowed in this position")
            }
            ']' -> {
                read() // Only accept it at end of document where it will fail to read because it failed in array content
                throw InvalidYamlContent("Invalid char $lastChar at this position")
            }
            '-' -> {
                read()
                if (this.lastChar.isWhitespace()) {
                    read() // Skip whitespace char
                    val indentToAdd = indents + if (parentReader !is IndentReader<*>) {
                        1
                    } else { 0 }

                    ArrayItemsReader(
                        yamlReader = this.yamlReader,
                        parentReader = this,
                        indentToAdd = indentToAdd
                    ).let {
                        this.currentReader = it
                        it.readUntilToken()
                    }
                } else {
                    plainStringReader("-")
                }
            }
            '?' -> {
                ExplicitMapKeyReader(
                    this.yamlReader,
                    this
                ) {
                    this.jsonTokenCreator(it)
                }.let {
                    this.currentReader = it
                    it.readUntilToken()
                }
            }
            ':' -> {
                read()
                if(this.lastChar == ' ') {
                    TODO("Value reader")
                } else {
                    plainStringReader(":")
                }
            }
            '#' -> {
                CommentReader(this.yamlReader, this).let {
                    this.currentReader = it
                    it.readUntilToken()
                }
            }
            else -> this.plainStringReader("")
        }
    }

    private fun skipWhiteSpace(): Int {
        var indents = 0
        while (this.lastChar.isSpacing()) {
            indents++
            read()
        }
        return indents
    }

    private fun jsonTokenCreator(value: String?): JsonToken {
        if (this.mapKeyFound) {
            this.mapValueFound = true
            if (!this.isExplicitMap) {
                this.indentToAdd -= 1
            }
            return JsonToken.Value(value)
        } else {
            skipWhiteSpace()
            if (this.lastChar == ':') {
                read()
                if (this.lastChar.isWhitespace()) {
                    if (this.lastChar.isLineBreak()) {
                        IndentReader(this.yamlReader, this).let {
                            this.currentReader = it
                        }
                    }

                     return this.foundMapKey(this.isExplicitMap)?.let {
                        this.hasFoundFieldName = JsonToken.FieldName(value)
                        it
                    } ?: JsonToken.FieldName(value)
                } else {
                    throw InvalidYamlContent("There should be whitespace after :")
                }
            }
        }

        return JsonToken.Value(value)
    }

    private fun plainStringReader(startWith: String): JsonToken {
        return PlainStringReader(
            this.yamlReader,
            this,
            startWith
        ) {
            this.jsonTokenCreator(it)
        }.let {
            this.currentReader = it
            it.readUntilToken()
        }
    }

    override fun foundMapKey(isExplicitMap: Boolean): JsonToken? {
        this.isExplicitMap = isExplicitMap
        if (!this.isExplicitMap) {
            this.indentToAdd += 1
        }
        if (this.mapKeyFound) {
            throw InvalidYamlContent("Already found mapping key. No other : allowed")
        }
        this.mapKeyFound = true
        return this.parentReader.foundMapKey(isExplicitMap)
    }

    override fun <P> newIndentLevel(indentCount: Int, parentReader: P): JsonToken
            where P : YamlCharReader,
                  P : IsYamlCharWithChildrenReader,
                  P : IsYamlCharWithIndentsReader {
        if (mapKeyFound) {
            mapValueFound = true
        }
        return this.parentReader.newIndentLevel(indentCount, parentReader)
    }

    override fun continueIndentLevel() = this.readUntilToken()

    override fun endIndentLevel(indentCount: Int, tokenToReturn: (() -> JsonToken)?): JsonToken {
        if (mapKeyFound) {
            if (tokenToReturn != null) {
                this.parentReader.childIsDoneReading()
                this.yamlReader.hasUnclaimedIndenting(indentCount)
                return tokenToReturn()
            } else if (!mapValueFound) {
                return this.parentReader.continueIndentLevel()
            }
        }
        this.parentReader.childIsDoneReading()
        return this.parentReader.endIndentLevel(indentCount, tokenToReturn)
    }

    override fun indentCount() = this.parentReader.indentCountForChildren() + this.indentToAdd

    override fun indentCountForChildren() = this.indentCount()

    override fun childIsDoneReading() {
        when (this.currentReader) {
            is StringInSingleQuoteReader<*>, is StringInDoubleQuoteReader<*> -> {
                this.hasCompletedValueReading = true
            }
            else -> {}
        }

        if (this.mapValueFound) {
            this.parentReader.childIsDoneReading()
        } else {
            this.currentReader = this
        }
    }

    override fun handleReaderInterrupt(): JsonToken {
        return this.parentReader.handleReaderInterrupt()
    }
}