package maryk.core.json.yaml

import maryk.core.extensions.isLineBreak
import maryk.core.extensions.isSpacing
import maryk.core.json.JsonToken
import maryk.core.json.TokenType
import maryk.core.json.ValueType

/** Reads Lines with actual non whitespace chars */
internal class LineReader<out P>(
    yamlReader: YamlReaderImpl,
    parentReader: P,
    startTag: TokenType? = null,
    private var indentToAdd: Int = 0
) : YamlTagReader<P>(yamlReader, parentReader, PlainStyleMode.NORMAL, startTag),
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

    override fun readUntilToken(): JsonToken {
        if (this.isExplicitMap) {
            if (this.lastChar == ':') {
                read()
                if(this.lastChar == ' ') {
                    this.isExplicitMap = false
                    return this.readUntilToken()
                }
            }

            this.parentReader.childIsDoneReading()
            return JsonToken.Value(null, ValueType.Null)
        }

        val indents = skipWhiteSpace()

        return when(this.lastChar) {
            '\n', '\r' -> {
                read()
                if (this.hasCompletedValueReading) {
                    this.parentReader.childIsDoneReading()
                }
                @Suppress("UNCHECKED_CAST")
                IndentReader(this.yamlReader, this.currentReader as P, this.tag).let {
                    this.currentReader = it
                    it.readUntilToken()
                }
            }
            '\'' -> this.singleQuoteString()
            '\"' -> this.doubleQuoteString()
            '[' -> this.flowSequenceReader()
            '{' -> this.flowMapReader()
            ',' -> {
                throw InvalidYamlContent("Invalid char $lastChar at this position")
            }
            '|' -> {
                read()
                return LiteralStringReader(
                    this.yamlReader,
                    this
                ) {
                    this.jsonTokenCreator(it, false)
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
                    this.jsonTokenCreator(it, false)
                }.let {
                    this.currentReader = it
                    it.readUntilToken()
                }
            }
            '!' -> this.tagReader()
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

                    ArrayItemsReader(
                        yamlReader = this.yamlReader,
                        parentReader = this,
                        indentToAdd = indents,
                        startTag = this.tag
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
                    this.jsonTokenCreator(it, false)
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

    override fun jsonTokenCreator(value: String?, isPlainStringReader: Boolean): JsonToken {
        if (this.mapKeyFound) {
            this.mapValueFound = true
            if (!this.isExplicitMap) {
                this.indentToAdd -= 1
            }
            return createYamlValueToken(value, this.tag, isPlainStringReader).also {
                // Unset so it can find more map keys if it is an embedded explicit map
                this.mapKeyFound = false
            }
        } else {
            skipWhiteSpace()
            if (this.parentReader is ExplicitMapKeyReader<*> && this.currentReader != this) {
                return JsonToken.FieldName(value)
            } else if (this.lastChar == ':') {
                read()
                if (this.lastChar.isWhitespace()) {
                    if (this.lastChar.isLineBreak()) {
                        IndentReader(this.yamlReader, this).let {
                            this.currentReader = it
                        }
                    }

                    return this.foundMapKey(this.isExplicitMap)?.let {
                        this.yamlReader.pushToken(JsonToken.FieldName(value))
                        it
                    } ?: JsonToken.FieldName(value)
                } else {
                    throw InvalidYamlContent("There should be whitespace after :")
                }
            }
        }

        return createYamlValueToken(value, this.tag, isPlainStringReader)
    }

    override fun foundMapKey(isExplicitMap: Boolean): JsonToken? {
        if (this.mapKeyFound && !isExplicitMap) {
            throw InvalidYamlContent("Already found mapping key. No other : allowed")
        }

        // break off since already processed
        if (this.mapKeyFound) {
            return null
        }

        if (isExplicitMap) {
            this.isExplicitMap = isExplicitMap
        } else if (!this.mapKeyFound) {
            this.indentToAdd += 1
        }

        this.mapKeyFound = true
        return this.parentReader.foundMapKey(isExplicitMap).also {
            this.tag = null
        }
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

    override fun endIndentLevel(indentCount: Int, tokenToReturn: (() -> JsonToken)?): JsonToken {
        if (mapKeyFound) {
            if (tokenToReturn != null) {
                return tokenToReturn().also {
                    if(this.parentReader.indentCountForChildren() >= indentCount){
                        this.parentReader.childIsDoneReading()
                        this.yamlReader.hasUnclaimedIndenting(indentCount)
                    }
                }
            } else if (!mapValueFound) {
                return this.parentReader.continueIndentLevel(this.tag)
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
        if (this.mapKeyFound && this.isExplicitMap) {
            if (!this.mapValueFound) {
                this.mapValueFound = true
                return JsonToken.Value(null, ValueType.Null)
            }
        }

        return this.parentReader.handleReaderInterrupt()
    }
}
