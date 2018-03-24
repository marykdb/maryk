package maryk.core.json.yaml

import maryk.core.json.ArrayType
import maryk.core.json.JsonToken
import maryk.core.json.MapType
import maryk.core.json.TokenType

private enum class FlowSequenceState {
    START, VALUE_START, EXPLICIT_KEY, COMPLEX_KEY, MAP_VALUE_AFTER_COMPLEX_KEY, KEY, VALUE, MAP_VALUE, MAP_END, STOP
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
    private var cachedCall: (() -> JsonToken)? = null

    override fun readUntilToken(): JsonToken {
        return when(this.state) {
            FlowSequenceState.START -> {
                this.state = FlowSequenceState.VALUE_START
                this.tag?.let {
                    this.tag = null
                    JsonToken.StartArray(
                        it as? ArrayType ?: throw InvalidYamlContent("Can only use sequence tags on sequences")
                    )
                } ?: JsonToken.SimpleStartArray
            }
            FlowSequenceState.COMPLEX_KEY -> this.jsonTokenCreator(null, false)
            else -> {
                while(this.lastChar.isWhitespace()) {
                    read()
                }

                return when(this.lastChar) {
                    '\'' -> this.singleQuoteString()
                    '\"' -> this.doubleQuoteString()
                    '{' -> this.flowMapReader().let(this::checkComplexFieldAndReturn)
                    '[' -> this.flowSequenceReader().let(this::checkComplexFieldAndReturn)
                    '!' -> this.tagReader()
                    '&' -> this.anchorReader().let {
                        this.currentReader = it
                        it.readUntilToken()
                    }
                    '*' -> this.aliasReader()
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
                        if (this.state != FlowSequenceState.MAP_VALUE_AFTER_COMPLEX_KEY) {
                            this.state = FlowSequenceState.KEY
                        }

                        return tokenReturner {
                            this.readUntilToken()
                        }
                    }
                    ']' -> {
                        tokenReturner {
                            this.state = FlowSequenceState.STOP
                            read()
                            this.parentReader.childIsDoneReading(true)
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
    }

    private fun checkComplexFieldAndReturn(jsonToken: JsonToken): JsonToken {
        if (this.state == FlowSequenceState.KEY) {
            this.state = FlowSequenceState.COMPLEX_KEY
            this.yamlReader.pushToken(jsonToken)
            return JsonToken.StartComplexFieldName
        }
        return jsonToken
    }

    private fun tokenReturner(doIfNoToken: () -> JsonToken): JsonToken {
        this.cachedCall?.let {
            this.cachedCall = null
            if (this.state == FlowSequenceState.VALUE_START) {
                this.state = FlowSequenceState.VALUE
            }
            return it()
        }

        return if (this.state == FlowSequenceState.MAP_END) {
            this.state = FlowSequenceState.VALUE_START
            JsonToken.EndObject
        } else if (this.state == FlowSequenceState.KEY || this.state == FlowSequenceState.MAP_VALUE) {
            this.jsonTokenCreator(null, false)
        } else {
            doIfNoToken()
        }
    }

    override fun jsonTokenCreator(value: String?, isPlainStringReader: Boolean): JsonToken = when(this.state) {
        FlowSequenceState.START -> throw InvalidYamlContent("Sequence cannot be in start mode")
        FlowSequenceState.EXPLICIT_KEY -> {
            this.state = FlowSequenceState.KEY
            this.startObject()
        }
        FlowSequenceState.VALUE_START -> {
            this.cachedCall = { this.jsonTokenCreator(value, isPlainStringReader) }
            this.readUntilToken()
        }
        FlowSequenceState.KEY -> {
            this.state = FlowSequenceState.MAP_VALUE
            checkAndCreateFieldName(value, isPlainStringReader)
        }
        FlowSequenceState.COMPLEX_KEY -> {
            this.state = FlowSequenceState.MAP_VALUE_AFTER_COMPLEX_KEY
            JsonToken.EndComplexFieldName
        }
        FlowSequenceState.VALUE -> {
            this.state = FlowSequenceState.VALUE_START
            createYamlValueToken(value, this.tag, isPlainStringReader)
        }
        FlowSequenceState.MAP_VALUE, FlowSequenceState.MAP_VALUE_AFTER_COMPLEX_KEY -> {
            this.state = FlowSequenceState.MAP_END
            createYamlValueToken(value, this.tag, isPlainStringReader)
        }
        FlowSequenceState.MAP_END, FlowSequenceState.STOP -> {
            throw InvalidYamlContent("Not a content token creator")
        }
    }

    override fun foundMap(isExplicitMap: Boolean) =
        if (this.state == FlowSequenceState.VALUE_START) {
            startObject()
        } else {
            null
        }.also {
            this.state = FlowSequenceState.MAP_VALUE
        }

    private fun startObject(): JsonToken {
        return this.tag?.let {
            JsonToken.StartObject(
                this.tag as? MapType ?: throw InvalidYamlContent("$tag should be a map type")
            ).also {
                this.tag = null
            }
        } ?: JsonToken.SimpleStartObject
    }

    override fun handleReaderInterrupt(): JsonToken {
        if (this.state != FlowSequenceState.STOP) {
            throw InvalidYamlContent("Sequences started with [ should always end with a ]")
        }
        this.parentReader.childIsDoneReading(true)
        return JsonToken.EndArray
    }
}
