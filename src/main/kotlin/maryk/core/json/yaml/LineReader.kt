package maryk.core.json.yaml

import maryk.core.json.JsonToken

/** Reads Lines with actual non whitespace chars */
internal class LineReader<out P>(
    yamlReader: YamlReader,
    parentReader: P,
    private var indentToAdd: Int = 0,
    private var isInsideArray: Boolean = false
) : YamlCharWithParentReader<P>(yamlReader, parentReader),
    IsYamlCharWithIndentsReader,
    IsYamlCharWithChildrenReader
        where P : YamlCharReader,
              P : IsYamlCharWithChildrenReader,
              P : IsYamlCharWithIndentsReader
{
    private var hasValue = false
    private var mapItemFound = false

    override fun readUntilToken(): JsonToken {
        val indents = skipWhiteSpace()

        return when(this.lastChar) {
            '\n' -> {
                read()
                if (this.hasValue) {
                    this.parentReader.childIsDoneReading()
                }
                IndentReader(this.yamlReader, this).let {
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
                FlowArrayItemsReader(
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
            '!', '>', '|' -> {
                TODO("Not supported yet")
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
                read()
                if(this.lastChar == ' ') {
                    TODO("Key reader")
                } else {
                    plainStringReader("?")
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
                TODO("Comment reader")
            }
            else -> this.plainStringReader("")
        }
    }

    private fun skipWhiteSpace(): Int {
        var indents = 0
        while (this.lastChar in arrayOf(' ', '\t')) {
            indents++
            read()
        }
        return indents
    }

    private fun jsonTokenCreator(value: String?): JsonToken {
        if (this.mapItemFound) {
            JsonToken.ObjectValue(value)
        } else {
            skipWhiteSpace()
            if (this.lastChar == ':') {
                read()
                return if (this.lastChar == ' ') {
                    JsonToken.FieldName(value)
                } else {
                    throw InvalidYamlContent("There should be a space after :")
                }
            }
        }

        return if (this.isInsideArray) {
            JsonToken.ArrayValue(value)
        } else {
            JsonToken.ObjectValue(value)
        }

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

    override fun foundIndentType(type: IndentObjectType): JsonToken? {
        this.indentToAdd += 1
        this.mapItemFound = true
        return this.parentReader.foundIndentType(type)
    }

    override fun <P> newIndentLevel(parentReader: P)
            where P : YamlCharReader,
                  P : IsYamlCharWithChildrenReader,
                  P : IsYamlCharWithIndentsReader = this.parentReader.newIndentLevel(parentReader)

    override fun continueIndentLevel(): JsonToken {
        return this.parentReader.continueIndentLevel()
    }

    override fun endIndentLevel(indentCount: Int, tokenToReturn: JsonToken?): JsonToken {
        if (mapItemFound) {
            return if (tokenToReturn != null) {
                this.parentReader.childIsDoneReading()
                this.yamlReader.hasUnclaimedIndenting(indentCount)
                tokenToReturn
            } else {
                this.parentReader.continueIndentLevel()
            }
        }
        return this.parentReader.endIndentLevel(indentCount, tokenToReturn)
    }

    override fun indentCount() = this.parentReader.indentCountForChildren() + this.indentToAdd

    override fun indentCountForChildren() = this.indentCount()

    override fun childIsDoneReading() {
        when (this.currentReader) {
            is StringInSingleQuoteReader<*>, is StringInDoubleQuoteReader<*> -> {
                this.hasValue = true
            }
            else -> {}
        }

        this.currentReader = this
    }

    override fun handleReaderInterrupt(): JsonToken {
        return this.parentReader.handleReaderInterrupt()
    }
}