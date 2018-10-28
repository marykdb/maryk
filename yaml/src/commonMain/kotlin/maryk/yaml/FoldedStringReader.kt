package maryk.yaml

import maryk.json.JsonToken
import maryk.json.TokenType
import maryk.lib.extensions.isLineBreak

/** Folded style string reader */
internal class FoldedStringReader<P>(
    yamlReader: YamlReaderImpl,
    parentReader: P,
    jsonTokenConstructor: (String?) -> JsonToken
) : LiteralStringReader<P>(yamlReader, parentReader, jsonTokenConstructor)
        where P : YamlCharReader,
              P : IsYamlCharWithIndentsReader
{
    override fun readUntilToken(extraIndent: Int, tag: TokenType?): JsonToken {
        // Read options and end at first line break
        readStartForOptionsAndReturnIndent("Folded >")

        val startIndentCount = findAndSetStartingIndentation()

        var previousWasOnBaseIndent = this.indentCount?.let { it == startIndentCount } ?: true

        loop@while(true) {
            when (this.lastChar) {
                '\n', '\r' -> {
                    this.foundLineBreaks = 1
                    val subtractLineBreak = previousWasOnBaseIndent || this.storedValue.isEmpty()
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
                        if(currentIndentCount == this.indentCount!!) {
                            break@whitespace
                        }
                    }

                    if (currentIndentCount < this.indentCount!!) {
                        this.yamlReader.setUnclaimedIndenting(currentIndentCount)
                        break@loop
                    }

                    if (subtractLineBreak) {
                        this.foundLineBreaks--
                    }

                    for (it in 0 until this.foundLineBreaks) {
                        this.storedValue += '\n'
                    }

                    if(this.lastChar == ' ') {
                        if (previousWasOnBaseIndent) {
                            this.storedValue += '\n'
                        }
                        previousWasOnBaseIndent = false
                    } else {
                        if (this.foundLineBreaks == 0) {
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

        return this.createTokenAndClose()
    }
}
