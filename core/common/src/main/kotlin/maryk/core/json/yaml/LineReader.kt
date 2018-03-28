package maryk.core.json.yaml

import maryk.core.extensions.isLineBreak
import maryk.core.extensions.isSpacing
import maryk.core.json.ExceptionWhileReadingJson
import maryk.core.json.JsonToken
import maryk.core.json.TokenType
import maryk.core.json.ValueType

/** Reads Lines with actual non whitespace chars */
internal class LineReader<out P>(
    yamlReader: YamlReaderImpl,
    parentReader: P,
    var startsAtNewLine: Boolean,
    var isExplicitMap: Boolean = false,
    private var indentToAdd: Int = 0
) : YamlTagReader<P>(yamlReader, parentReader, PlainStyleMode.NORMAL),
    IsYamlCharWithIndentsReader,
    IsYamlCharWithChildrenReader
        where P : YamlCharReader,
              P : IsYamlCharWithChildrenReader,
              P : IsYamlCharWithIndentsReader
{
    private var hasCompletedValueReading = false
    private var mapKeyFound = false
    private var mapValueFound = false

    override fun readUntilToken(tag: TokenType?): JsonToken {
        val indents = if(!this.startsAtNewLine) {
            skipWhiteSpace()
        } else {
            this.startsAtNewLine = false
            skipWhiteSpace()
            0
        }

        return when(this.lastChar) {
            '\n', '\r' -> {
                read()
                if (this.hasCompletedValueReading) {
                    this.parentReader.childIsDoneReading(false)
                }
                @Suppress("UNCHECKED_CAST")
                IndentReader(this.yamlReader, this.currentReader as P).let {
                    this.currentReader = it
                    it.readUntilToken(tag)
                }
            }
            '\'' -> this.singleQuoteString(tag)
            '\"' -> this.doubleQuoteString(tag)
            '[' -> this.flowSequenceReader(tag)
            '{' -> this.flowMapReader(tag)
            ',' -> {
                throw InvalidYamlContent("Invalid char $lastChar at this position")
            }
            '|' -> {
                read()
                return LiteralStringReader(
                    this.yamlReader,
                    this
                ) {
                    this.jsonTokenCreator(it, false, tag)
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
                    this.jsonTokenCreator(it, false, tag)
                }.let {
                    this.currentReader = it
                    it.readUntilToken()
                }
            }
            '!' -> this.tagReader()
            '&' -> this.anchorReader().let {
                this.currentReader = it
                it.readUntilToken()
            }
            '*' -> this.aliasReader()
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
                    SequenceItemsReader(
                        yamlReader = this.yamlReader,
                        parentReader = this,
                        indentToAdd = indents
                    ).let {
                        this.currentReader = it
                        it.readUntilToken(tag)
                    }
                } else {
                    plainStringReader("-", tag)
                }
            }
            '?' -> {
                try {
                    read()
                }catch (e: ExceptionWhileReadingJson) {
                    this.currentReader = ExplicitMapKeyReader(
                        this.yamlReader,
                        MapItemsReader(
                            this.yamlReader,
                            this,
                            true
                        )
                    ) {
                        this.jsonTokenCreator(it, false, tag)
                    }
                    throw e
                }
                // If it turns out to not be an explicit key make it a Plain String reader
                if (!this.lastChar.isWhitespace()) {
                    @Suppress("UNCHECKED_CAST")
                    return PlainStringReader(
                        this.yamlReader,
                        this.currentReader as P,
                        "?"
                    ) {
                        this.jsonTokenCreator(it, true, tag)
                    }.let {
                        this.currentReader = it
                        it.readUntilToken()
                    }
                }

                this.foundMap(true, tag)?.let {
                    @Suppress("UNCHECKED_CAST")
                    this.currentReader = ExplicitMapKeyReader(
                        this.yamlReader,
                        this.currentReader as P
                    ) {
                        this.jsonTokenCreator(it, false, tag)
                    }
                    return it
                }

                ExplicitMapKeyReader(
                    this.yamlReader,
                    this
                ) {
                    this.jsonTokenCreator(it, false, tag)
                }.let {
                    this.currentReader = it
                    it.readUntilToken()
                }
            }
            ':' -> {
                read()
                if(this.lastChar == ' ') {
                    this.mapKeyFound = true
                    this.readUntilToken()
                } else {
                    plainStringReader(":", tag)
                }
            }
            '#' -> {
                CommentReader(this.yamlReader, this).let {
                    this.currentReader = it
                    it.readUntilToken()
                }
            }
            else -> this.plainStringReader("", tag)
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

    override fun jsonTokenCreator(value: String?, isPlainStringReader: Boolean, tag: TokenType?): JsonToken {
        if (this.mapKeyFound) {
            this.mapValueFound = true
            if(this.parentReader is ExplicitMapKeyReader<*>) {
                this.indentToAdd -= 1
            }
            return createYamlValueToken(value, tag, isPlainStringReader).also {
                // Unset so it can find more map keys if it is an embedded explicit map
                this.mapKeyFound = false
            }
        } else {
            skipWhiteSpace()
            if (this.parentReader is ExplicitMapKeyReader<*> && this.currentReader != this) {
                return this.checkAndCreateFieldName(value, isPlainStringReader)
            } else if (this.lastChar == ':' && !this.yamlReader.hasUnclaimedIndenting()) {
                read()
                if (this.lastChar.isWhitespace()) {
                    if (this.lastChar.isLineBreak()) {
                        IndentReader(this.yamlReader, this).let {
                            this.currentReader = it
                        }
                    }

                    val fieldName = this.checkAndCreateFieldName(value, isPlainStringReader)
                    return this.foundMap(this.isExplicitMap, tag)?.let {
                        this.yamlReader.pushToken(fieldName)
                        it
                    } ?: fieldName
                } else {
                    throw InvalidYamlContent("There should be whitespace after :")
                }
            }
        }

        return createYamlValueToken(value, tag, isPlainStringReader)
    }

    override fun foundMap(isExplicitMap: Boolean, tag: TokenType?): JsonToken? {
        if (this.mapKeyFound && !isExplicitMap) {
            throw InvalidYamlContent("Already found mapping key. No other : allowed")
        }

        // break off since already processed
        if (this.mapKeyFound) {
            return null
        }

        if (isExplicitMap) {
            this.isExplicitMap = isExplicitMap
        }

        if(this.parentReader is ExplicitMapKeyReader<*>) {
            this.indentToAdd += 1
        }
        this.mapKeyFound = true
        return this.parentReader.foundMap(isExplicitMap, tag)
    }

    override fun <P> newIndentLevel(indentCount: Int, parentReader: P, tag: TokenType?): JsonToken
            where P : YamlCharReader,
                  P : IsYamlCharWithChildrenReader,
                  P : IsYamlCharWithIndentsReader {
        if (mapKeyFound) {
            mapValueFound = true
        }
        return this.parentReader.newIndentLevel(indentCount, parentReader, tag)
    }

    override fun endIndentLevel(
        indentCount: Int,
        tag: TokenType?,
        tokenToReturn: (() -> JsonToken)?
    ): JsonToken {
        this.parentReader.childIsDoneReading(false)
        return this.parentReader.endIndentLevel(indentCount, tag, tokenToReturn)
    }

    override fun indentCount() = this.parentReader.indentCountForChildren() + this.indentToAdd

    override fun indentCountForChildren() = this.indentCount()

    override fun childIsDoneReading(closeLineReader: Boolean) {
        when (this.currentReader) {
            is StringInSingleQuoteReader<*>, is StringInDoubleQuoteReader<*> -> {
                this.hasCompletedValueReading = true
            }
            else -> {}
        }

        if (closeLineReader) {
            this.mapValueFound = true
            this.parentReader.childIsDoneReading(false)
        } else {
            this.currentReader = this
        }
    }

    override fun handleReaderInterrupt(): JsonToken {
        if (this.mapKeyFound && this.isExplicitMap) {
            if (!this.mapValueFound) {
                this.mapValueFound = true
                return JsonToken.Value(null, ValueType.Null)
            }
        }

        return this.parentReader.handleReaderInterrupt()
    }
}
