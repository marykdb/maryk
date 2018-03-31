package maryk.core.json.yaml

import maryk.core.extensions.isLineBreak
import maryk.core.extensions.isSpacing
import maryk.core.json.ExceptionWhileReadingJson
import maryk.core.json.JsonToken
import maryk.core.json.TokenType

/** Reads Lines with actual non whitespace chars */
internal class LineReader<out P>(
    yamlReader: YamlReaderImpl,
    parentReader: P,
    private var startsAtNewLine: Boolean,
    private var isExplicitMap: Boolean = false,
    private var indentToAdd: Int = 0
) : YamlCharWithParentAndIndentReader<P>(yamlReader, parentReader),
    IsYamlCharWithIndentsReader,
    IsYamlCharWithChildrenReader
        where P : YamlCharReader,
              P : IsYamlCharWithChildrenReader,
              P : IsYamlCharWithIndentsReader
{
    private var hasCompletedValueReading = false
    private var mapKeyFound = false

    override fun readUntilToken(extraIndent: Int, tag: TokenType?): JsonToken {
        val indents = if(!this.startsAtNewLine) {
            skipWhiteSpace().let {
                if (it == 1) 0 else it
            }
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
                    it.readUntilToken(0, tag)
                }
            }
            '\'' -> this.singleQuoteString(tag, indents, this::jsonTokenCreator)
            '\"' -> this.doubleQuoteString(tag, indents, this::jsonTokenCreator)
            '[' -> this.flowSequenceReader(tag)
            '{' -> this.flowMapReader(tag)
            ',' -> throw InvalidYamlContent("Invalid char $lastChar at this position")
            '|' -> {
                read()
                return LiteralStringReader(
                    this.yamlReader,
                    this
                ) {
                    this.jsonTokenCreator(it, false, tag)
                }.let {
                    this.currentReader = it
                    it.readUntilToken(0)
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
                    it.readUntilToken(0)
                }
            }
            '!' -> this.tagReader(indents)
            '&' -> this.anchorReader(extraIndent)
            '*' -> this.aliasReader(PlainStyleMode.NORMAL, extraIndent)
            '@', '`' -> throw InvalidYamlContent("Reserved indicators for future use and not supported by this reader")
            '%' -> throw InvalidYamlContent("Directive % indicator not allowed in this position")
            ']' -> throw InvalidYamlContent("Invalid char $lastChar at this position")
            '-' -> {
                read()
                if (this.lastChar.isWhitespace()) {
                    SequenceItemsReader(
                        yamlReader = this.yamlReader,
                        parentReader = this,
                        indentToAdd = indents
                    ).let {
                        this.currentReader = it
                        it.readUntilToken(0, tag)
                    }
                } else {
                    this.plainStringReader("-", tag, PlainStyleMode.NORMAL, indents, this::jsonTokenCreator)
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
                    )
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
                        it.readUntilToken(indents)
                    }
                }

                this.foundMap(true, tag, indents)?.let {
                    @Suppress("UNCHECKED_CAST")
                    this.currentReader = ExplicitMapKeyReader(
                        this.yamlReader,
                        this.currentReader as P
                    )
                    return it
                }

                ExplicitMapKeyReader(
                    this.yamlReader,
                    this
                ).let {
                    this.currentReader = it
                    it.readUntilToken(indents)
                }
            }
            ':' -> {
                read()
                if(this.lastChar == ' ') {
                    this.mapKeyFound = true
                    this.readUntilToken(0)
                } else {
                    plainStringReader(":", tag, PlainStyleMode.NORMAL, 0, this::jsonTokenCreator)
                }
            }
            '#' -> {
                CommentReader(this.yamlReader, this).let {
                    this.currentReader = it
                    it.readUntilToken(0)
                }
            }
            else -> this.plainStringReader("", tag, PlainStyleMode.NORMAL, indents, this::jsonTokenCreator)
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

    private fun jsonTokenCreator(value: String?, isPlainStringReader: Boolean, tag: TokenType?): JsonToken {
        if (this.mapKeyFound) {
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
                    return this.foundMap(this.isExplicitMap, tag, 0)?.let {
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

    override fun foundMap(isExplicitMap: Boolean, tag: TokenType?, startedAtIndent: Int): JsonToken? {
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
        return this.parentReader.foundMap(isExplicitMap, tag, startedAtIndent)
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
            this.parentReader.childIsDoneReading(false)
        } else {
            this.currentReader = this
        }
    }

    override fun handleReaderInterrupt(): JsonToken {
        return this.parentReader.handleReaderInterrupt()
    }
}

/**
 * Creates a LineReader below [parentReader].
 * Set [startsAtNewLine] to true if it was started on a new line.
 * Set [isExplicitMap] to true if LineReader is below explicit map
 */
internal fun <P> P.lineReader(parentReader: P, startsAtNewLine: Boolean, isExplicitMap: Boolean = false): LineReader<P>
        where P : IsYamlCharWithChildrenReader,
              P : YamlCharReader,
              P : IsYamlCharWithIndentsReader =
    LineReader(
        yamlReader = yamlReader,
        parentReader = parentReader,
        startsAtNewLine = startsAtNewLine,
        isExplicitMap = isExplicitMap
    ).apply {
        this.currentReader = this
    }
