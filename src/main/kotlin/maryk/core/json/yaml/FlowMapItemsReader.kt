package maryk.core.json.yaml

import maryk.core.json.JsonToken

private enum class FlowMapMode {
    START, KEY, VALUE, SEPARATOR, STOP
}

/** Reader for flow Map Items {key1: value1, key2: value2} */
internal class FlowMapItemsReader<out P>(
    yamlReader: YamlReaderImpl,
    parentReader: P
) : YamlCharWithParentReader<P>(yamlReader, parentReader),
    IsYamlCharWithChildrenReader
        where P : YamlCharReader,
              P : IsYamlCharWithChildrenReader
{
    private var mode = FlowMapMode.START

    override fun readUntilToken(): JsonToken {
        return if (this.mode == FlowMapMode.START) {
            this.mode = FlowMapMode.KEY
            JsonToken.StartObject
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
                    StringInSingleQuoteReader(this.yamlReader, this, this::constructToken).let {
                        this.currentReader = it
                        it.readUntilToken()
                    }
                }
                '\"' -> {
                    read()
                    StringInDoubleQuoteReader(this.yamlReader, this, this::constructToken).let {
                        this.currentReader = it
                        it.readUntilToken()
                    }
                }
                '[' -> {
                    read()
                    FlowArrayItemsReader(
                        yamlReader = this.yamlReader,
                        parentReader = this
                    ).let {
                        this.currentReader = it
                        it.readUntilToken()
                    }
                }
                '{' -> {
                    read()
                    FlowMapItemsReader(
                        yamlReader = this.yamlReader,
                        parentReader = this
                    ).let {
                        this.currentReader = it
                        it.readUntilToken()
                    }
                }
                '-' -> {
                    read()
                    if (this.lastChar.isWhitespace()) {
                        throw InvalidYamlContent("Expected a comma")
                    } else {
                        TODO("simple string reader or fail")
                    }
                }
                ',' -> {
                    if(this.mode != FlowMapMode.SEPARATOR) {
                        return this.constructToken(null)
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
                else -> {
                    throw InvalidYamlContent("Unknown character '$lastChar' found")
                }
            }
        }
    }

    private fun constructToken(value: String?) = when(mode) {
        FlowMapMode.START -> throw InvalidYamlContent("Map cannot be in start mode")
        FlowMapMode.KEY -> {
            this.mode = FlowMapMode.VALUE
            JsonToken.FieldName(value)
        }
        FlowMapMode.VALUE -> {
            this.mode = FlowMapMode.SEPARATOR
            JsonToken.ObjectValue(value)
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

    override fun childIsDoneReading() {
        this.currentReader = this
    }

    override fun handleReaderInterrupt(): JsonToken {
        this.parentReader.childIsDoneReading()
        return JsonToken.EndObject
    }
}