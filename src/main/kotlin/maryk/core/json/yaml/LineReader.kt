package maryk.core.json.yaml

import maryk.core.json.InvalidJsonContent
import maryk.core.json.JsonToken

/** Reads Lines with actual non whitespace chars */
internal class LineReader<P>(
    yamlReader: YamlReader,
    parentReader: P,
    private val jsonTokenCreator: (String?) -> JsonToken
) : YamlCharWithParentReader<P>(yamlReader, parentReader),
    IsYamlCharWithIndentsReader,
    IsYamlCharWithChildrenReader
        where P : YamlCharReader,
              P : IsYamlCharWithChildrenReader,
              P : IsYamlCharWithIndentsReader
{
    private var hasValue = false

    override fun readUntilToken(): JsonToken {
        var indents = 0

        while(this.lastChar in arrayOf(' ', '\t')) {
            indents++
            read()
        }

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
            ']' -> {
                read() // Only accept it at end of

                throw InvalidJsonContent("Unknown char")
            }
            '-' -> {
                read()
                if (this.lastChar.isWhitespace()) {
                    read() // Skip whitespace char
                    val indentToAdd = indents + if (parentReader !is IndentReader<*>) {
                        2
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
                    TODO("simple string reader or fail")
                }
            }
            else -> {
                throw InvalidJsonContent("Unknown character '$lastChar' found")
            }
        }
    }

    override fun continueIndentLevel(): JsonToken {
        return this.parentReader.continueIndentLevel()
    }

    override fun endIndentLevel(indentCount: Int, tokenToReturn: JsonToken?) =
        super.parentReader.endIndentLevel(indentCount, tokenToReturn)

    override fun indentCount() = this.parentReader.indentCount()

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