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
    private var startsAtNewLine: Boolean
) : YamlCharWithParentAndIndentReader<P>(yamlReader, parentReader),
    IsYamlCharWithIndentsReader,
    IsYamlCharWithChildrenReader
        where P : YamlCharReader,
              P : IsYamlCharWithChildrenReader,
              P : IsYamlCharWithIndentsReader
{
    private var indentToAdd: Int = 0
    private var mapKeyFound = false

    override fun readUntilToken(extraIndent: Int, tag: TokenType?): JsonToken {
        val indents = if(!this.startsAtNewLine) {
            skipWhiteSpace().let {
                if (it == 1) 0 else it
            }
        } else {
            this.startsAtNewLine = false
            skipWhiteSpace()
            extraIndent
        }

        return when(this.lastChar) {
            '\n', '\r' -> {
                read()
                @Suppress("UNCHECKED_CAST")
                IndentReader(this.yamlReader, this.currentReader as P).let {
                    this.currentReader = it
                    it.readUntilToken(0, tag)
                }
            }
            '\'' -> this.singleQuoteString(tag, this::jsonTokenCreator)
            '\"' -> this.doubleQuoteString(tag, this::jsonTokenCreator)
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
            '!' -> this.tagReader { this.continueIndentLevel(extraIndent, it) }
            '&' -> this.anchorReader { this.continueIndentLevel(extraIndent, tag) }
            '*' -> this.aliasReader(PlainStyleMode.NORMAL)
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
                            this
                        )
                    )
                    throw e
                }
                // If it turns out to not be an explicit key make it a Plain String reader
                if (!this.lastChar.isWhitespace()) {
                    @Suppress("UNCHECKED_CAST")
                    return (this.currentReader as P).plainStringReader(
                        "?",
                        tag,
                        PlainStyleMode.NORMAL,
                        indents,
                        this::jsonTokenCreator
                    )
                }

                this.foundMap(tag, indents)?.let {
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
                    this.foundMap(tag, indents)?.let {
                        this.yamlReader.pushToken(this.readUntilToken(0))
                        return it
                    }
                    this.readUntilToken(0)
                } else {
                    plainStringReader(":", tag, PlainStyleMode.NORMAL, 0, this::jsonTokenCreator)
                }
            }
            '#' -> {
                this.commentReader {
                    this.readUntilToken(0, tag)
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
        }

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
                return this.foundMap(tag, 0)?.let {
                    this.yamlReader.pushToken(fieldName)
                    it
                } ?: fieldName
            } else {
                throw InvalidYamlContent("There should be whitespace after :")
            }
        }

        return createYamlValueToken(value, tag, isPlainStringReader)
    }

    override fun foundMap(tag: TokenType?, startedAtIndent: Int): JsonToken? {
        if (this.mapKeyFound) {
            throw InvalidYamlContent("Already found mapping key. No other : allowed")
        }

        if(this.parentReader is ExplicitMapKeyReader<*>) {
            this.indentToAdd += 1
        }
        this.mapKeyFound = true
        return this.parentReader.foundMap(tag, startedAtIndent)
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
        if (closeLineReader) {
            this.parentReader.childIsDoneReading(false)
        } else {
            this.currentReader = this
        }
    }
}

/**
 * Creates a LineReader below [parentReader].
 * Set [startsAtNewLine] to true if it was started on a new line.
 */
internal fun <P> P.lineReader(parentReader: P, startsAtNewLine: Boolean): LineReader<P>
        where P : IsYamlCharWithChildrenReader,
              P : YamlCharReader,
              P : IsYamlCharWithIndentsReader =
    LineReader(
        yamlReader = yamlReader,
        parentReader = parentReader,
        startsAtNewLine = startsAtNewLine
    ).apply {
        this.currentReader = this
    }


/**
 * Creates a LineReader below [parentReader].
 * Set [startsAtNewLine] to true if it was started on a new line.
 */
internal fun <P> P.newLineReader(startsAtNewLine: Boolean, tag: TokenType?, extraIndent: Int, jsonTokenCreator: JsonTokenCreator): JsonToken
        where P : IsYamlCharWithChildrenReader,
              P : YamlCharReader,
              P : IsYamlCharWithIndentsReader {
    fun skipWhiteSpace(): Int {
        var indents = 0
        while (this.lastChar.isSpacing()) {
            indents++
            read()
        }
        return indents
    }

    val indents = if (!startsAtNewLine) {
        skipWhiteSpace()
    } else {
//        startsAtNewLine = false
        skipWhiteSpace()
        extraIndent
    }

    return when (this.lastChar) {
        '\n', '\r' -> {
            read()
            val indentCount = this.yamlReader.skipEmptyLinesAndCommentsAndCountIndents()
            val currentIndentCount = this.indentCount()
            if (indentCount < currentIndentCount) {
                return this.endIndentLevel(indentCount, tag, null)
            } else {
                this.newLineReader(true, tag, this.indentCount() - indentCount, jsonTokenCreator)
            }
        }
        '\'' -> this.singleQuoteString(tag, jsonTokenCreator)
        '\"' -> this.doubleQuoteString(tag, jsonTokenCreator)
        '[' -> this.flowSequenceReader(tag)
        '{' -> this.flowMapReader(tag)
        ',' -> throw InvalidYamlContent("Invalid char $lastChar at this position")
        '|' -> {
            read()
            return LiteralStringReader(
                this.yamlReader,
                this
            ) {
                jsonTokenCreator(it, false, tag)
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
                jsonTokenCreator(it, false, tag)
            }.let {
                this.currentReader = it
                it.readUntilToken(0)
            }
        }
        '!' -> this.tagReader { this.newLineReader(true, it, indents, jsonTokenCreator) }
        '&' -> this.anchorReader { this.newLineReader(true, tag, indents, jsonTokenCreator) }
        '*' -> this.aliasReader(PlainStyleMode.NORMAL)
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
                this.plainStringReader("-", tag, PlainStyleMode.NORMAL, indents, jsonTokenCreator)
            }
        }
        '?' -> {
            try {
                read()
            } catch (e: ExceptionWhileReadingJson) {
                this.currentReader = ExplicitMapKeyReader(
                    this.yamlReader,
                    MapItemsReader(
                        this.yamlReader,
                        this
                    )
                )
                throw e
            }
            // If it turns out to not be an explicit key make it a Plain String reader
            if (!this.lastChar.isWhitespace()) {
                @Suppress("UNCHECKED_CAST")
                return (this.currentReader as P).plainStringReader(
                    "?",
                    tag,
                    PlainStyleMode.NORMAL,
                    indents,
                    jsonTokenCreator
                )
            }

            this.foundMap(tag, indents)?.let {
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
            if (this.lastChar == ' ') {
                this.foundMap(tag, indents)?.let {
                    this.yamlReader.pushToken(this.readUntilToken(0))
                    return it
                }
                this.readUntilToken(0)
            } else {
                plainStringReader(":", tag, PlainStyleMode.NORMAL, 0, jsonTokenCreator)
            }
        }
        '#' -> {
            this.commentReader {
                this.readUntilToken(0, tag)
            }
        }
        else -> this.plainStringReader("", tag, PlainStyleMode.NORMAL, indents, jsonTokenCreator)
    }
}
