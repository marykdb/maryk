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
    parentReader: P,
    startTag: TokenType?
) : YamlTagReader<P>(yamlReader, parentReader, PlainStyleMode.FLOW_MAP, startTag),
    IsYamlCharWithChildrenReader, IsYamlCharWithIndentsReader
        where P : YamlCharReader,
              P : IsYamlCharWithChildrenReader,
              P : IsYamlCharWithIndentsReader
{
    private var state = FlowMapState.START

    override fun readUntilToken(): JsonToken {
        return when(this.state) {
            FlowMapState.START -> {
                this.state = FlowMapState.KEY
                this.tag?.let {
                    this.tag = null
                    (it as? MapType)?.let {
                        JsonToken.StartObject(it)
                    } ?: throw InvalidYamlContent("Cannot use non map tags on maps")
                } ?: JsonToken.SimpleStartObject
            }
            FlowMapState.COMPLEX_KEY -> this.jsonTokenCreator(null, false)
            else -> {
                while(this.lastChar.isWhitespace()) {
                    read()
                }

                return when(this.lastChar) {
                    '\'' -> this.singleQuoteString()
                    '\"' -> this.doubleQuoteString()
                    '[' -> {
                        this.flowSequenceReader()
                            .let(this::checkComplexFieldAndReturn)
                    }
                    '{' -> {
                        this.flowMapReader()
                            .let(this::checkComplexFieldAndReturn)
                    }
                    '!' -> this.tagReader()
                    '-' -> {
                        read()
                        if (this.lastChar.isWhitespace()) {
                            throw InvalidYamlContent("Expected a comma")
                        } else this.plainStringReader("-")
                    }
                    ',' -> {
                        if(this.state != FlowMapState.SEPARATOR) {
                            return this.jsonTokenCreator(null, false)
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
                            return this.jsonTokenCreator(null, false)
                        }
                        this.state = FlowMapState.STOP

                        read()
                        this.parentReader.childIsDoneReading()
                        JsonToken.EndObject
                    }
                    '?' -> {
                        read()
                        if (this.lastChar.isWhitespace()) {
                            if (this.state == FlowMapState.EXPLICIT_KEY) {
                                throw InvalidYamlContent("Cannot have two ? explicit keys in a row")
                            }
                            this.state = FlowMapState.EXPLICIT_KEY
                            this.jsonTokenCreator(null, false)
                        } else if(this.lastChar == ',' || this.lastChar == ':') {
                            this.jsonTokenCreator(null, false)
                        } else {
                            this.plainStringReader("?")
                        }
                    }
                    '|', '>' -> throw InvalidYamlContent("Unsupported character $lastChar in flow map")
                    else -> this.plainStringReader("")
                }
            }
        }
    }

    override fun jsonTokenCreator(value: String?, isPlainStringReader: Boolean) = when(state) {
        FlowMapState.START -> throw InvalidYamlContent("Map cannot be in start state")
        FlowMapState.EXPLICIT_KEY -> {
            this.state = FlowMapState.KEY
            this.readUntilToken()
        }
        FlowMapState.KEY -> {
            this.state = FlowMapState.VALUE
            JsonToken.FieldName(value)
        }
        FlowMapState.COMPLEX_KEY -> {
            this.state = FlowMapState.VALUE
            JsonToken.EndComplexFieldName
        }
        FlowMapState.VALUE -> {
            this.state = FlowMapState.SEPARATOR
            createYamlValueToken(value, this.tag, isPlainStringReader)
        }
        FlowMapState.SEPARATOR -> {
            // If last state was separator next one will be key
            this.state = FlowMapState.VALUE
            JsonToken.FieldName(value)
        }
        FlowMapState.STOP -> {
            this.state = FlowMapState.STOP
            JsonToken.EndObject
        }
    }

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
        this.parentReader.childIsDoneReading()
        return JsonToken.EndObject
    }
}
