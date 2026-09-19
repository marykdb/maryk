package maryk.yaml

import maryk.json.JsonToken
import maryk.json.TokenType

/**
 * Reads indents on a new line until a char is found and then determines to which reader to continue.
 * It will either continue on current reader or end the reader
 */
internal fun IsYamlCharWithIndentsReader.readIndentsAndContinue(tag: TokenType?, onContinue: (extraIndent: Int) -> JsonToken): JsonToken {
    val indentCount = this.yamlReader.skipEmptyLinesAndCommentsAndCountIndents()
    val currentIndentCount = this.indentCount()
    return if (indentCount < currentIndentCount) {
        this.endIndentLevel(indentCount, tag, null)
    } else {
        onContinue(indentCount - currentIndentCount)
    }
}
