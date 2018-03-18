package maryk.core.json.yaml

import maryk.core.json.ArrayType
import maryk.core.json.JsonToken
import maryk.core.json.TokenType

private enum class FlowSequenceState {
    START, VALUE_START, EXPLICIT_KEY, KEY, VALUE, MAP_VALUE, MAP_END, STOP
}

/** Reader for flow sequences [item1, item2, item3] */
internal class FlowSequenceReader<out P>(
    yamlReader: YamlReaderImpl,
    parentReader: P,
    startTag: TokenType?
) : YamlTagReader<P>(yamlReader, parentReader, PlainStyleMode.FLOW_COLLECTION, startTag),
    IsYamlCharWithChildrenReader,
    IsYamlCharWithIndentsReader
        where P : YamlCharReader,
              P : IsYamlCharWithChildrenReader,
              P : IsYamlCharWithIndentsReader
{
    private var state = FlowSequenceState.START

    override fun readUntilToken(): JsonToken {
        return if (this.state == FlowSequenceState.START) {
            this.state = FlowSequenceState.VALUE_START
            this.tag?.let {
                this.tag = null
                JsonToken.StartArray(
                    it as? ArrayType ?: throw InvalidYamlContent("Can only use sequence tags on sequences")
                )
            } ?: JsonToken.SimpleStartArray
        } else {
            while(this.lastChar.isWhitespace()) {
                read()
            }

            return when(this.lastChar) {
                '\'' -> this.singleQuoteString()
                '\"' -> this.doubleQuoteString()
                '{' -> this.flowMapReader()
                '[' -> this.flowSequenceReader()
                '!' -> this.tagReader()
                '-' -> {
                    read()
                    if (this.lastChar.isWhitespace()) {
                        throw InvalidYamlContent("Expected a comma")
                    } else {
                        this.plainStringReader("-")
                    }
                }
                ',' -> {
                    if(this.state != FlowSequenceState.MAP_END && this.state != FlowSequenceState.VALUE_START) {
                        return this.jsonTokenCreator(null, false)
                    }

                    read()
                    return tokenReturner {
                        this.readUntilToken()
                    }
                }
                ':' -> {
                    read()
                    this.state = FlowSequenceState.KEY

                    return tokenReturner {
                        this.readUntilToken()
                    }
                }
                ']' -> {
                    tokenReturner {
                        read()
                        this.parentReader.childIsDoneReading()
                        JsonToken.EndArray
                    }
                }
                '}' -> {
                    read() // This should be handled in Map reader. Otherwise incorrect content
                    throw InvalidYamlContent("Invalid char $lastChar at this position")
                }
                '?' -> {
                    read()
                    if (this.lastChar.isWhitespace()) {
                        if (this.state == FlowSequenceState.EXPLICIT_KEY) {
                            throw InvalidYamlContent("Cannot have two ? explicit keys in a row")
                        }
                        this.state = FlowSequenceState.EXPLICIT_KEY
                        this.jsonTokenCreator(null, false)
                    } else if(this.lastChar == ',' || this.lastChar == ':') {
                        this.state = FlowSequenceState.EXPLICIT_KEY
                        this.jsonTokenCreator(null, false)
                    } else {
                        this.state = FlowSequenceState.KEY
                        this.plainStringReader("?")
                    }
                }
                '|', '>' -> throw InvalidYamlContent("Unsupported character $lastChar in flow array")
                else -> this.plainStringReader("")
            }
        }
    }

    private fun tokenReturner(doIfNoToken: () -> JsonToken): JsonToken {
        return this.cachedCall?.let {
            this.cachedCall = null
            if (this.state == FlowSequenceState.VALUE_START) {
                this.state = FlowSequenceState.VALUE
            }
            it()
        } ?: if (this.state == FlowSequenceState.MAP_END) {
            this.state = FlowSequenceState.VALUE_START
            JsonToken.EndObject
        } else if (this.state == FlowSequenceState.KEY) {
            this.jsonTokenCreator(null, false)
        } else {
            doIfNoToken()
        }
    }

    private var cachedCall: (() -> JsonToken)? = null

    override fun jsonTokenCreator(value: String?, isPlainStringReader: Boolean): JsonToken = when(this.state) {
        FlowSequenceState.START -> throw InvalidYamlContent("Sequence cannot be in start mode")
        FlowSequenceState.EXPLICIT_KEY -> {
            this.state = FlowSequenceState.KEY
            JsonToken.SimpleStartObject
        }
        FlowSequenceState.VALUE_START -> {
            this.cachedCall = { this.jsonTokenCreator(value, isPlainStringReader) }
            this.readUntilToken()
        }
        FlowSequenceState.KEY -> {
            this.state = FlowSequenceState.MAP_VALUE
            JsonToken.FieldName(value)
        }
        FlowSequenceState.VALUE -> {
            this.state = FlowSequenceState.VALUE_START
            createYamlValueToken(value, this.tag, isPlainStringReader)
        }
        FlowSequenceState.MAP_VALUE -> {
            this.state = FlowSequenceState.MAP_END
            createYamlValueToken(value, this.tag, isPlainStringReader)
        }
        FlowSequenceState.MAP_END, FlowSequenceState.STOP -> {
            throw InvalidYamlContent("Not a content token creator")
        }
    }

    override fun foundMapKey(isExplicitMap: Boolean) =
        if (this.state == FlowSequenceState.VALUE_START) {
            JsonToken.SimpleStartObject
        } else {
            null
        }.also {
            this.state = FlowSequenceState.MAP_VALUE
        }

    override fun handleReaderInterrupt(): JsonToken {
        this.currentReader = this.parentReader
        return JsonToken.EndArray
    }
}