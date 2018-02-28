package maryk.core.json.yaml

import maryk.core.extensions.isLineBreak
import maryk.core.extensions.isSpacing
import maryk.core.json.JsonToken

/** Literal style string reader */
internal class FoldedStringReader<out P>(
    yamlReader: YamlReaderImpl,
    parentReader: P,
    jsonTokenConstructor: (String?) -> JsonToken
) : LiteralStringReader<P>(yamlReader, parentReader, jsonTokenConstructor)
        where P : YamlCharReader,
              P : IsYamlCharWithChildrenReader,
              P : IsYamlCharWithIndentsReader
{
    override fun readUntilToken(): JsonToken {
        // Previous reader left it just after >
        while (this.lastChar.isSpacing()) {
            read()
        }
        if (!this.lastChar.isLineBreak()) {
            throw InvalidYamlContent("Folded > should always be followed by a linebreak")
        }
        read()

        val parentIndentCount = this.parentReader.indentCountForChildren()

        this.indentCount = findStartingIndentation(parentIndentCount)

        var previousWasOnBaseIndent = true

        loop@while(true) {
            when (this.lastChar) {
                '\n', '\r' -> {
                    if (!previousWasOnBaseIndent) {
                        this.storedValue += this.lastChar
                    }
                    read()

                    var currentIndentCount = 0
                    var hasExtraBreak = false
                    whitespace@while (this.lastChar.isWhitespace()) {
                        if (this.lastChar.isLineBreak()) {
                            currentIndentCount = 0
                            hasExtraBreak = true
                            this.storedValue += this.lastChar
                        } else {
                            currentIndentCount++
                        }
                        read()
                        if(currentIndentCount == this.indentCount!!) {
                            break@whitespace
                        }
                    }

                    if (currentIndentCount < parentIndentCount) {
                        this.storedValue += '\n'
                        break@loop
                    }

                    if(this.lastChar == ' ') {
                        if (previousWasOnBaseIndent) {
                            this.storedValue += '\n'
                        }
                        previousWasOnBaseIndent = false
                    } else {
                        if (!hasExtraBreak) {
                            this.storedValue += ' '
                        }
                        previousWasOnBaseIndent = true
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
}