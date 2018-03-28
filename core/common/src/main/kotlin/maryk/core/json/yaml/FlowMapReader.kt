package maryk.core.json.yaml

import maryk.core.json.JsonToken
import maryk.core.json.MapType
import maryk.core.json.TokenType

private enum class FlowMapState {
    START, EXPLICIT_KEY, KEY, COMPLEX_KEY, VALUE, SEPARATOR, STOP
}

/** Reader for flow Map Items {key1: value1, key2: value2} */
internal class FlowMapItemsReader<out P>(
    yamlReader: YamlReaderImpl,
    parentReader: P
) : YamlTagReader<P>(yamlReader, parentReader, PlainStyleMode.FLOW_MAP),
    IsYamlCharWithChildrenReader, IsYamlCharWithIndentsReader
        where P : YamlCharReader,
              P : IsYamlCharWithChildrenReader,
              P : IsYamlCharWithIndentsReader
{
    private var state = FlowMapState.START
    private val fieldNames = mutableListOf<String?>()

    override fun readUntilToken(tag: TokenType?): JsonToken {
        return when(this.state) {
            FlowMapState.START -> {
                this.state = FlowMapState.KEY
                tag?.let {
                    (it as? MapType)?.let {
                        JsonToken.StartObject(it)
                    } ?: throw InvalidYamlContent("Cannot use non map tags on maps")
                } ?: JsonToken.SimpleStartObject
            }
            FlowMapState.COMPLEX_KEY -> this.jsonTokenCreator(null, false, tag)
            else -> {
                while(this.lastChar.isWhitespace()) {
                    read()
                }

                return when(this.lastChar) {
                    '\'' -> this.singleQuoteString(tag)
                    '\"' -> this.doubleQuoteString(tag)
                    '[' -> {
                        this.flowSequenceReader(tag)
                            .let(this::checkComplexFieldAndReturn)
                    }
                    '{' -> {
                        this.flowMapReader(tag)
                            .let(this::checkComplexFieldAndReturn)
                    }
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
                        } else this.plainStringReader("-", tag)
                    }
                    ',' -> {
                        if(this.state != FlowMapState.SEPARATOR) {
                            return this.jsonTokenCreator(null, false, tag)
                        }

                        read()
                        this.readUntilToken()
                    }
                    ':' -> {
                        read()
                        this.readUntilToken()
                    }
                    ']' -> {
                        read() // This should be handled in Array reader. Otherwise incorrect content
                        throw InvalidYamlContent("Invalid char $lastChar at this position")
                    }
                    '}' -> {
                        if(this.state != FlowMapState.SEPARATOR) {
                            return this.jsonTokenCreator(null, false, tag)
                        }
                        this.state = FlowMapState.STOP

                        read()
                        this.parentReader.childIsDoneReading(true)
                        JsonToken.EndObject
                    }
                    '?' -> {
                        read()
                        if (this.lastChar.isWhitespace()) {
                            if (this.state == FlowMapState.EXPLICIT_KEY) {
                                throw InvalidYamlContent("Cannot have two ? explicit keys in a row")
                            }
                            this.state = FlowMapState.EXPLICIT_KEY
                            this.jsonTokenCreator(null, false, tag)
                        } else if(this.lastChar == ',' || this.lastChar == ':') {
                            this.jsonTokenCreator(null, false, tag)
                        } else {
                            this.plainStringReader("?", tag)
                        }
                    }
                    '|', '>' -> throw InvalidYamlContent("Unsupported character $lastChar in flow map")
                    else -> this.plainStringReader("", tag)
                }
            }
        }
    }

    override fun jsonTokenCreator(value: String?, isPlainStringReader: Boolean, tag: TokenType?) = when(state) {
        FlowMapState.START -> throw InvalidYamlContent("Map cannot be in start state")
        FlowMapState.EXPLICIT_KEY -> {
            this.state = FlowMapState.KEY
            this.readUntilToken()
        }
        FlowMapState.KEY, FlowMapState.SEPARATOR -> {
            this.state = FlowMapState.VALUE
            this.checkAndCreateFieldName(value, isPlainStringReader)
        }
        FlowMapState.COMPLEX_KEY -> {
            this.state = FlowMapState.VALUE
            JsonToken.EndComplexFieldName
        }
        FlowMapState.VALUE -> {
            this.state = FlowMapState.SEPARATOR
            createYamlValueToken(value, tag, isPlainStringReader)
        }
        FlowMapState.STOP -> {
            this.state = FlowMapState.STOP
            JsonToken.EndObject
        }
    }

    override fun checkAndCreateFieldName(fieldName: String?, isPlainStringReader: Boolean) =
        checkAndCreateFieldName(this.fieldNames, fieldName, isPlainStringReader)

    private fun checkComplexFieldAndReturn(jsonToken: JsonToken): JsonToken {
        if (this.state == FlowMapState.KEY) {
            this.yamlReader.pushToken(jsonToken)
            this.state = FlowMapState.COMPLEX_KEY
            return JsonToken.StartComplexFieldName
        }
        this.state = FlowMapState.SEPARATOR
        return jsonToken
    }

    override fun handleReaderInterrupt(): JsonToken {
        if (this.state != FlowMapState.STOP) {
            throw InvalidYamlContent("Maps started with { should always end with a }")
        }
        this.parentReader.childIsDoneReading(true)
        return JsonToken.EndObject
    }
}
