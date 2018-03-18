package maryk.core.json.yaml

import maryk.core.json.JsonToken
import maryk.core.json.MapType
import maryk.core.json.TokenType

private enum class FlowMapState {
    START, EXPLICIT_KEY, KEY, VALUE, SEPARATOR, STOP
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
        return if (this.state == FlowMapState.START) {
            this.state = FlowMapState.KEY
            this.tag?.let {
                this.tag = null
                (it as? MapType)?.let {
                    JsonToken.StartObject(it)
                } ?: throw InvalidYamlContent("Cannot use non map tags on maps")
            } ?: JsonToken.SimpleStartObject
        } else {
            while(this.lastChar.isWhitespace()) {
                read()
            }

            return when(this.lastChar) {
                '\'' -> this.singleQuoteString()
                '\"' -> this.doubleQuoteString()
                '[' -> {
                    this.state = FlowMapState.SEPARATOR
                    this.flowSequenceReader()
                }
                '{' -> {
                    this.state = FlowMapState.SEPARATOR
                    this.flowMapReader()
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

    override fun handleReaderInterrupt(): JsonToken {
        this.parentReader.childIsDoneReading()
        return JsonToken.EndObject
    }
}