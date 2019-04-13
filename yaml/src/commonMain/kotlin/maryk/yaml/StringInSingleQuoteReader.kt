package maryk.yaml

import maryk.json.ExceptionWhileReadingJson
import maryk.json.JsonToken
import maryk.json.TokenType

/**
 * Reads a single quoted string.
 * Pass [tag] to set type on Value.
 * [jsonTokenCreator] creates the right jsonToken. Could be field name or value.
 */
internal fun IsYamlCharReader.singleQuoteString(
    tag: TokenType?,
    extraIndentAtStart: Int,
    jsonTokenCreator: JsonTokenCreator
): JsonToken {
    var aQuoteFound = false
    var storedValue: String? = ""

    try {
        read() // skip starting quote

        loop@ while (true) {
            if (lastChar == '\'') {
                if (aQuoteFound) {
                    storedValue += lastChar
                    aQuoteFound = false
                } else {
                    aQuoteFound = true
                }
            } else {
                if (aQuoteFound) {
                    break@loop
                } else {
                    storedValue += lastChar
                }
            }
            read()
        }

        return jsonTokenCreator(storedValue, false, tag, extraIndentAtStart)
    } catch (e: ExceptionWhileReadingJson) {
        this.yamlReader.hasException = true

        if (aQuoteFound) {
            return jsonTokenCreator(storedValue, false, tag, extraIndentAtStart)
        } else {
            throw InvalidYamlContent("Single quoted string was never closed")
        }
    }
}
