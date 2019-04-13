package maryk.yaml

import maryk.json.ExceptionWhileReadingJson
import maryk.json.JsonToken

/** Reads an alias with [mode] and returns first found token */
internal fun IsYamlCharReader.aliasReader(mode: PlainStyleMode): JsonToken {
    var alias = ""

    fun pushStoredTokens(): JsonToken {
        val tokens = this.yamlReader.getTokensForAlias(alias)
        for (index in 1 until tokens.size) {
            this.yamlReader.pushToken(tokens[index])
        }
        return tokens[0]
    }

    try {
        read()

        val forbiddenChars = when (mode) {
            PlainStyleMode.FLOW_SEQUENCE -> arrayOf(' ', '\r', '\n', '\t', ',', ']')
            PlainStyleMode.FLOW_MAP -> arrayOf(' ', '\r', '\n', '\t', ',', '}')
            else -> arrayOf(' ', '\r', '\n', '\t')
        }

        while (this.lastChar !in forbiddenChars) {
            alias += this.lastChar
            read()
        }

        return pushStoredTokens()
    } catch (e: ExceptionWhileReadingJson) {
        this.yamlReader.hasException = true
        return pushStoredTokens()
    }
}
