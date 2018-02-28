package maryk.core.json.yaml

import maryk.core.extensions.isLineBreak
import maryk.core.extensions.isSpacing
import maryk.core.json.JsonToken

/** Literal style string reader */
internal class LiteralStringReader<out P>(
    yamlReader: YamlReaderImpl,
    parentReader: P,
    private var jsonTokenConstructor: (String?) -> JsonToken
) : YamlCharWithParentReader<P>(yamlReader, parentReader)
        where P : YamlCharReader,
              P : IsYamlCharWithChildrenReader,
              P : IsYamlCharWithIndentsReader
{
    private var storedValue: String = ""
    private var indentCount: Int? = null

    override fun readUntilToken(): JsonToken {
        // Previous reader left it just after |
        while (this.lastChar.isSpacing()) {
            read()
        }
        if (!this.lastChar.isLineBreak()) {
            throw InvalidYamlContent("Literal | should always be followed by a linebreak")
        }
        read()

        val parentIndentCount = this.parentReader.indentCountForChildren()

        this.indentCount = findStartingIndentation(parentIndentCount)

        loop@while(true) {
            when (this.lastChar) {
                '\n', '\r' -> {
                    this.storedValue += this.lastChar
                    read()
                    var currentIndentCount = 0
                    whitespace@while (this.lastChar.isWhitespace()) {
                        if (this.lastChar.isLineBreak()) {
                            currentIndentCount = 0
                            this.storedValue += this.lastChar
                        } else {
                            currentIndentCount++
                        }
                        read()
                        if(currentIndentCount == this.indentCount) {
                            break@whitespace
                        }
                    }

                    if (currentIndentCount < parentIndentCount) {
                        break@loop
                    }
                }
                else -> {
                    this.storeCharAndProceed()
                }
            }
        }

        this.setToParent()
        return this.createToken()
    }

    private fun findStartingIndentation(parentIndentCount: Int): Int {
        var currentIndentCount = 0
        while (this.lastChar.isWhitespace()) {
            if (this.lastChar.isLineBreak()) {
                this.storedValue += this.lastChar
                currentIndentCount = 0
            } else {
                currentIndentCount++
            }
            read()
        }
        if (currentIndentCount < parentIndentCount) {
            throw InvalidYamlContent("Literal scalar with | should contain a value")
        }
        return currentIndentCount
    }

    private fun storeCharAndProceed() {
        this.storedValue += lastChar
        read()
    }

    override fun handleReaderInterrupt(): JsonToken {
        this.storedValue += '\n'
        this.setToParent()
        return this.createToken()
    }

    private fun setToParent() {
        this.parentReader.childIsDoneReading()
        this.currentReader.let {
            if (it is LineReader<*>) {
                (it.parentReader as IsYamlCharWithChildrenReader).childIsDoneReading()
            }
        }
    }

    private fun createToken() = this.jsonTokenConstructor(this.storedValue)
}