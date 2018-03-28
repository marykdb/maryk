package maryk.core.json.yaml

import maryk.core.extensions.digitCharsWithoutZero
import maryk.core.extensions.isLineBreak
import maryk.core.extensions.isSpacing
import maryk.core.json.JsonToken
import maryk.core.json.TokenType

internal enum class ChompStyle {
    STRIP, CLIP, KEEP
}

/** Literal style string reader */
internal open class LiteralStringReader<out P>(
    yamlReader: YamlReaderImpl,
    parentReader: P,
    private var jsonTokenConstructor: (String?) -> JsonToken
) : YamlCharWithParentReader<P>(yamlReader, parentReader)
        where P : YamlCharReader,
              P : IsYamlCharWithChildrenReader,
              P : IsYamlCharWithIndentsReader
{
    private val parentIndentCount = this.parentReader.let {
        if (it is LineReader<*>) {
            (it.parentReader as IsYamlCharWithIndentsReader).indentCount()
        } else {
            it.indentCount()
        }
    }

    protected var storedValue: String = ""
    protected var indentCount: Int? = null
    protected var foundLineBreaks: Int = 0
    protected var chompStyle: ChompStyle = ChompStyle.CLIP

    override fun readUntilToken(tag: TokenType?): JsonToken {
        // Read options and end at first line break
        readStartForOptionsAndReturnIndent("Literal |")

        findAndSetStartingIndentation()

        loop@while(true) {
            when (this.lastChar) {
                '\n', '\r' -> {
                    this.storedValue += this.lastChar
                    this.foundLineBreaks = 0
                    read()
                    var currentIndentCount = 0
                    whitespace@while (this.lastChar.isWhitespace()) {
                        if (this.lastChar.isLineBreak()) {
                            currentIndentCount = 0
                            this.foundLineBreaks++
                        } else {
                            currentIndentCount++
                        }
                        read()
                        if(currentIndentCount == this.indentCount) {
                            break@whitespace
                        }
                    }

                    if (currentIndentCount < this.indentCount!!) {
                        this.yamlReader.setUnclaimedIndenting(currentIndentCount)
                        break@loop
                    }

                    for (it in 0 until this.foundLineBreaks) {
                        this.storedValue += '\n'
                    }
                }
                else -> {
                    this.storeCharAndProceed()
                }
            }
        }

        return this.createTokenAndClose()
    }

    protected fun readStartForOptionsAndReturnIndent(description: String) {
        options@while (true) {
            when (this.lastChar) {
                in digitCharsWithoutZero -> {
                    if(this.indentCount != null) {
                        throw InvalidYamlContent("Cannot define indentation twice")
                    }
                    this.indentCount = this.lastChar.toString().toInt() + this.parentIndentCount
                }
                '+' -> {
                    if(this.chompStyle != ChompStyle.CLIP) {
                        throw InvalidYamlContent("Cannot define chomping twice")
                    }
                    this.chompStyle = ChompStyle.KEEP
                }
                '-' -> {
                    if(this.chompStyle != ChompStyle.CLIP) {
                        throw InvalidYamlContent("Cannot define chomping twice")
                    }
                    this.chompStyle = ChompStyle.STRIP
                }
                else -> {
                    break@options
                }
            }
            read()
        }

        // Previous reader left it just after |
        while (this.lastChar.isSpacing()) {
            read()
        }
        if (!this.lastChar.isLineBreak()) {
            throw InvalidYamlContent("$description should always be followed by a linebreak")
        }
    }

    protected fun findAndSetStartingIndentation(): Int {
        read() // go past line break

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
        if (currentIndentCount < this.parentIndentCount) {
            throw InvalidYamlContent("Literal scalar with | should contain a value")
        }
        if (this.indentCount == null) {
            this.indentCount = currentIndentCount
        } else if(this.indentCount!! > currentIndentCount) {
            throw InvalidYamlContent("Expected a continuation of block")
        } else {
            if (currentIndentCount > this.indentCount!!) {
                for (it in 0..currentIndentCount - this.indentCount!!) {
                    this.storedValue += ' '
                }
            }
        }
        return currentIndentCount
    }

    protected fun storeCharAndProceed() {
        this.storedValue += lastChar
        read()
    }

    override fun handleReaderInterrupt(): JsonToken {
        return this.createTokenAndClose()
    }

    protected fun createTokenAndClose(): JsonToken {
        when(this.chompStyle) {
            ChompStyle.KEEP -> for (it in 0 until this.foundLineBreaks) {
                this.storedValue += '\n'
            }
            ChompStyle.CLIP -> {
                this.storedValue = this.storedValue.trimEnd('\n')
                this.storedValue += '\n'
            }
            ChompStyle.STRIP -> {
                this.storedValue = this.storedValue.trimEnd('\n')
            }
        }

        this.parentReader.childIsDoneReading(false)

        return this.jsonTokenConstructor(this.storedValue)
    }
}