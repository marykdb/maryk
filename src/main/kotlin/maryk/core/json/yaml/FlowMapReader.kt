package maryk.core.json.yaml

import maryk.core.json.JsonToken
import maryk.core.json.MapType
import maryk.core.json.TokenType

private enum class FlowMapMode {
    START, EXPLICITKEY, KEY, VALUE, SEPARATOR, STOP
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
    private var mode = FlowMapMode.START
    override fun readUntilToken(): JsonToken {
        return if (this.mode == FlowMapMode.START) {
            this.mode = FlowMapMode.KEY
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
                ':' -> {
                    read()
                    this.readUntilToken()
                }
                '\'' -> {
                    read()
                    StringInSingleQuoteReader(this.yamlReader, this, { this.jsonTokenCreator(it, false) }).let {
                        this.currentReader = it
                        it.readUntilToken()
                    }
                }
                '\"' -> {
                    read()
                    StringInDoubleQuoteReader(this.yamlReader, this, { this.jsonTokenCreator(it, false) }).let {
                        this.currentReader = it
                        it.readUntilToken()
                    }
                }
                '!' -> {
                    TagReader(this.yamlReader, this).let {
                        this.currentReader = it
                        it.readUntilToken()
                    }
                }
                '[' -> {
                    read()
                    this.mode = FlowMapMode.SEPARATOR
                    FlowSequenceReader(
                        yamlReader = this.yamlReader,
                        parentReader = this,
                        startTag = this.tag
                    ).let {
                        this.currentReader = it
                        it.readUntilToken()
                    }
                }
                '{' -> {
                    read()
                    this.mode = FlowMapMode.SEPARATOR
                    FlowMapItemsReader(
                        yamlReader = this.yamlReader,
                        parentReader = this,
                        startTag = this.tag
                    ).let {
                        this.currentReader = it
                        it.readUntilToken()
                    }
                }
                '-' -> {
                    read()
                    if (this.lastChar.isWhitespace()) {
                        throw InvalidYamlContent("Expected a comma")
                    } else this.plainStringReader("-")
                }
                ',' -> {
                    if(this.mode != FlowMapMode.SEPARATOR) {
                        return this.jsonTokenCreator(null, false)
                    }

                    read()
                    this.readUntilToken()
                }
                ']' -> {
                    read() // This should only happen in Array reader. Otherwise incorrect content
                    throw InvalidYamlContent("Invalid char $lastChar at this position")
                }
                '}' -> {
                    read()
                    this.parentReader.childIsDoneReading()
                    JsonToken.EndObject
                }
                '?' -> {
                    read()
                    if (this.lastChar.isWhitespace()) {
                        if (this.mode == FlowMapMode.EXPLICITKEY) {
                            throw InvalidYamlContent("Cannot have two ? explicit keys in a row")
                        }
                        this.mode = FlowMapMode.EXPLICITKEY
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

    override fun jsonTokenCreator(value: String?, isPlainStringReader: Boolean) = when(mode) {
        FlowMapMode.START -> throw InvalidYamlContent("Map cannot be in start mode")
        FlowMapMode.EXPLICITKEY -> {
            this.mode = FlowMapMode.KEY
            this.readUntilToken()
        }
        FlowMapMode.KEY -> {
            this.mode = FlowMapMode.VALUE
            JsonToken.FieldName(value)
        }
        FlowMapMode.VALUE -> {
            this.mode = FlowMapMode.SEPARATOR
            createYamlValueToken(value, this.tag, isPlainStringReader)
        }
        FlowMapMode.SEPARATOR -> {
            // If last mode was separator next one will be key
            this.mode = FlowMapMode.VALUE
            JsonToken.FieldName(value)
        }
        FlowMapMode.STOP -> {
            this.mode = FlowMapMode.STOP
            JsonToken.EndObject
        }
    }

    override fun handleReaderInterrupt(): JsonToken {
        this.parentReader.childIsDoneReading()
        return JsonToken.EndObject
    }
}