package maryk.core.json.yaml

import maryk.core.json.JsonToken
import maryk.core.json.TokenType

/** Reads an *alias to return possible anchored tags */
internal class AliasReader<out P>(
    yamlReader: YamlReaderImpl,
    parentReader: P,
    private var mode: PlainStyleMode
) : YamlCharWithParentReader<P>(yamlReader, parentReader)
        where P : YamlCharReader,
              P : IsYamlCharWithChildrenReader,
              P : IsYamlCharWithIndentsReader
{
    private var alias = ""

    override fun readUntilToken(tag: TokenType?): JsonToken {
        read()

        val forbiddenChars = when(this.mode) {
            PlainStyleMode.FLOW_SEQUENCE -> arrayOf(' ', '\r', '\n', '\t', ',', ']')
            PlainStyleMode.FLOW_MAP -> arrayOf(' ', '\r', '\n', '\t', ',', '}')
            else -> arrayOf(' ', '\r', '\n', '\t')
        }

        while(this.lastChar !in forbiddenChars) {
            alias += this.lastChar
            read()
        }

        return pushStoredTokens()
    }

    /** Push all tokens that were saved for alias to yamlreader and return first one immediately */
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

/** Creates an alias reader within scope of YamlCharReader with [mode] and returns first found token */
internal fun <P> P.aliasReader(mode: PlainStyleMode)
        where P : IsYamlCharWithChildrenReader,
              P : YamlCharReader,
              P : IsYamlCharWithIndentsReader =
    AliasReader(this.yamlReader, this, mode).let {
        this.currentReader = it
        it.readUntilToken()
    }
