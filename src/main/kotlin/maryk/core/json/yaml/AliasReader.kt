package maryk.core.json.yaml

import maryk.core.json.JsonToken

/** Reads an *alias to return possible anchored tags */
internal class AliasReader<out P>(
    yamlReader: YamlReaderImpl,
    parentReader: P
) : YamlCharWithParentReader<P>(yamlReader, parentReader)
        where P : YamlCharReader,
              P : IsYamlCharWithChildrenReader,
              P : IsYamlCharWithIndentsReader
{
    var alias = ""

    override fun readUntilToken(): JsonToken {
        read()

        while(!this.lastChar.isWhitespace()) {
            alias += this.lastChar
            read()
        }

        return pushStoredTokens()
    }

    private fun pushStoredTokens(): JsonToken {
        val tokens = this.yamlReader.getTokensForAlias(this.alias)

        for (index in 1 until tokens.size) {
            this.yamlReader.pushToken(tokens[index])
        }

        this.parentReader.childIsDoneReading(false)
        return tokens[0]
    }

    override fun handleReaderInterrupt(): JsonToken {
        return this.pushStoredTokens()
    }
}
