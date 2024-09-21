package maryk.yaml

import maryk.json.ExceptionWhileReadingJson
import maryk.json.JsonToken
import maryk.yaml.PlainStyleMode.FLOW_MAP
import maryk.yaml.PlainStyleMode.FLOW_SEQUENCE

/** Reads an alias with [mode] and returns first found token */
internal fun IsYamlCharReader.aliasReader(mode: PlainStyleMode): JsonToken {
    var alias = ""

    fun pushStoredTokens(): JsonToken =
        yamlReader.getTokensForAlias(alias).also { tokens ->
            for (index in 1 until tokens.size) {
                yamlReader.pushToken(tokens[index])
            }
        }.first()

    try {
        read()

        val forbiddenChars = when (mode) {
            FLOW_SEQUENCE -> charArrayOf(' ', '\r', '\n', '\t', ',', ']')
            FLOW_MAP -> charArrayOf(' ', '\r', '\n', '\t', ',', '}')
            else -> charArrayOf(' ', '\r', '\n', '\t')
        }

        while (lastChar !in forbiddenChars) {
            alias += lastChar
            read()
        }

        return pushStoredTokens()
    } catch (_: ExceptionWhileReadingJson) {
        yamlReader.hasException = true
        return pushStoredTokens()
    }
}
