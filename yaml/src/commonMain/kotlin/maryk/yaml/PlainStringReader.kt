package maryk.yaml

import maryk.json.ExceptionWhileReadingJson
import maryk.json.JsonToken
import maryk.json.TokenType
import maryk.lib.extensions.isLineBreak
import maryk.yaml.PlainStyleMode.FLOW_MAP
import maryk.yaml.PlainStyleMode.FLOW_SEQUENCE

internal enum class PlainStyleMode {
    NORMAL, FLOW_SEQUENCE, FLOW_MAP
}

/**
 * Reads a plain String
 * Set [startWith] to set first characters
 * Pass [tag] to set type on Value.
 * Pass [extraIndent] to set at how many indents where present before plain string so it can be added as map start values
 * [flowMode] determines which characters can stop the reader
 * [jsonTokenCreator] creates the right jsonToken. Could be field name or value.
 */
internal fun IsYamlCharWithIndentsReader.plainStringReader(
    startWith: String,
    tag: TokenType?,
    flowMode: PlainStyleMode,
    extraIndent: Int,
    jsonTokenCreator: JsonTokenCreator
): JsonToken {
    var storedValue: String = startWith

    fun storeCharAndProceed() {
        storedValue += lastChar
        read()
    }

    fun createToken(): JsonToken {
        return jsonTokenCreator(storedValue.trim(), true, tag, extraIndent)
    }

    try {
        loop@ while (true) {
            when (this.lastChar) {
                '\n', '\r' -> {
                    storedValue = storedValue.trimEnd()

                    val currentIndentCount = this.yamlReader.skipEmptyLinesAndCommentsAndCountIndents()
                    val readerIndentCount = this.indentCount() + extraIndent + if (this is MapItemsReader<*>) 1 else 0

                    if (currentIndentCount < readerIndentCount) {
                        return this.endIndentLevel(currentIndentCount, tag) {
                            createToken()
                        }
                    } else {
                        storedValue += ' '
                    }
                }
                ':' -> {
                    read()
                    if (this.lastChar.isWhitespace()) {
                        // Only override token creators with non flow maps
                        if (flowMode != FLOW_MAP) {
                            if (!this.lastChar.isLineBreak()) {
                                read()
                            }

                            // If new map return Object Start and push new token
                            this.foundMap(tag, extraIndent)?.let {
                                this.yamlReader.pushToken(
                                    (this.currentReader as IsYamlCharWithIndentsReader).checkAndCreateFieldName(storedValue.trim(), true)
                                )
                                return it
                            }

                            return this.checkAndCreateFieldName(storedValue.trim(), true)
                        }

                        // Else return specific token
                        return createToken()
                    }
                    storedValue += ":$lastChar"
                    read()
                }
                '#' -> {
                    if (storedValue.last() == ' ') {
                        this.commentReader {
                            this.readUntilToken(0, tag)
                        }
                    }

                    storeCharAndProceed()
                }
                else -> {
                    when (flowMode) {
                        FLOW_SEQUENCE -> {
                            if (this.lastChar == ',' || this.lastChar == ']') {
                                return createToken()
                            }
                        }
                        FLOW_MAP -> {
                            if (this.lastChar == ',' || this.lastChar == '}') {
                                return createToken()
                            }
                        }
                        else -> Unit
                    }

                    storeCharAndProceed()
                }
            }
        }
    } catch (e: ExceptionWhileReadingJson) {
        this.yamlReader.hasException = true
        return createToken()
    }
}
