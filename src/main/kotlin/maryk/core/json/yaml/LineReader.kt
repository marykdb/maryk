package maryk.core.json.yaml

import maryk.core.json.InvalidJsonContent
import maryk.core.json.JsonToken

/** Reads Lines with actual non whitespace chars */
internal class LineReader(
    yamlReader: YamlReader,
    parentReader: YamlCharWithChildrenReader,
    private val jsonTokenCreator: (String?) -> JsonToken
) : YamlCharWithChildrenReader(yamlReader, parentReader) {
    private var hasValue = false

    override fun readUntilToken() = when(this.lastChar) {
        '\n' -> {
            read()
            if (!this.hasValue) {
                IndentReader(this.yamlReader, this).let {
                    this.currentReader = it
                    it.readUntilToken()
                }
            } else {
                this.parentReader!!.childIsDoneReading()
                this.currentReader.readUntilToken()
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
        '-' -> {
            read()
            when (this.lastChar) {
                ' ' -> {
                    ArrayItemsReader(
                        yamlReader = this.yamlReader,
                        parentReader = this
                    ).let {
                        this.currentReader = it
                        read() // Skip this char
                        it.readUntilToken()
                    }
                }
                else -> TODO("simple string reader or fail")
            }
        }
        else -> {
            throw InvalidJsonContent("Unknown character '$lastChar' found")
        }
    }

    override fun continueIndentLevel(): JsonToken {
        return this.parentReader!!.continueIndentLevel()
    }

    override fun endIndentLevel(indentCount: Int, tokenToReturn: JsonToken?) =
        super.parentReader!!.endIndentLevel(indentCount, tokenToReturn)

    override fun indentCount() = this.parentReader!!.indentCount()

    override fun childIsDoneReading() {
        when (this.currentReader) {
            is StringInSingleQuoteReader, is StringInDoubleQuoteReader -> {
                this.hasValue = true
            }
            else -> {}
        }

        this.currentReader = this
    }

    override fun handleReaderInterrupt(): JsonToken {
        return this.parentReader!!.handleReaderInterrupt()
    }
}