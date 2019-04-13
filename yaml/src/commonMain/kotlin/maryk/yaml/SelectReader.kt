package maryk.yaml

import maryk.json.ExceptionWhileReadingJson
import maryk.json.JsonToken
import maryk.json.TokenType
import maryk.lib.extensions.isSpacing

/**
 * Reads until is clear what is next token, selects relevant reader and continues reading.
 * Add [tag] for found tags, [extraIndent] on extra indent where it started reading.
 * Use [jsonTokenCreator] to create found tokens with which is relevant for maps where there also could be created fields
 * instead of values.
 * Set [startsAtNewLine] to true if it was started on a new line.
 */
internal fun <P: IsYamlCharWithIndentsReader> P.selectReaderAndRead(
    startsAtNewLine: Boolean,
    tag: TokenType?,
    extraIndent: Int,
    jsonTokenCreator: JsonTokenCreator
): JsonToken {
    var indents = extraIndent
    // count white space
    while (this.lastChar.isSpacing()) {
        indents++
        read()
    }

    return when (this.lastChar) {
        '\n', '\r' -> {
            read()
            val indentCount = this.yamlReader.skipEmptyLinesAndCommentsAndCountIndents()
            val currentIndentCount = this.indentCount()
            when {
                indentCount < currentIndentCount -> return this.endIndentLevel(indentCount, tag, null)
                indentCount == currentIndentCount -> return this.continueIndentLevel(0, tag)
                else -> this.selectReaderAndRead(true, tag, indentCount - currentIndentCount, jsonTokenCreator)
            }
        }
        '\'' -> this.singleQuoteString(tag, indents, jsonTokenCreator)
        '\"' -> this.doubleQuoteString(tag, indents, jsonTokenCreator)
        '[' -> this.flowSequenceReader(tag, 1)
        '{' -> this.flowMapReader(tag, 1)
        ',' -> throw InvalidYamlContent("Invalid char $lastChar at this position")
        '|' -> {
            read()
            return LiteralStringReader(
                this.yamlReader,
                this
            ) {
                jsonTokenCreator(it, false, tag, 0)
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
                jsonTokenCreator(it, false, tag, 0)
            }.let {
                this.currentReader = it
                it.readUntilToken(0)
            }
        }
        '!' -> this.tagReader { this.selectReaderAndRead(true, it, indents, jsonTokenCreator) }
        '&' -> this.anchorReader { this.selectReaderAndRead(true, tag, indents, jsonTokenCreator) }
        '*' -> this.aliasReader(PlainStyleMode.NORMAL)
        '@', '`' -> throw InvalidYamlContent("Reserved indicators for future use and not supported by this reader")
        '%' -> throw InvalidYamlContent("Directive % indicator not allowed in this position")
        ']' -> throw InvalidYamlContent("Invalid char $lastChar at this position")
        '-' -> {
            read()

            if (this.lastChar.isWhitespace()) {
                // Sequences can start at same level as map
                val mapCorrection = if (this is MapItemsReader<*> && !startsAtNewLine) -1 else 0
                SequenceItemsReader(
                    yamlReader = this.yamlReader,
                    parentReader = this,
                    indentToAdd = indents + mapCorrection
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
                    this.currentReader as MapItemsReader<*>
                )
                return it
            }

            ExplicitMapKeyReader(
                this.yamlReader,
                this as MapItemsReader<*>
            ).let {
                this.currentReader = it
                it.readUntilToken(indents)
            }
        }
        ':' -> {
            read()
            if (this.lastChar.isWhitespace()) {
                this.foundMap(tag, indents)?.let {
                    this.yamlReader.pushToken(this.readUntilToken(0))
                    return it
                }
                this.readUntilToken(0)
            } else {
                plainStringReader(":", tag, PlainStyleMode.NORMAL, 1, jsonTokenCreator)
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
