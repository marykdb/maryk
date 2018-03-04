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
    IsYamlCharWithChildrenReader, IsYamlCharWithIndentsReader
        where P : YamlCharReader,
              P : IsYamlCharWithChildrenReader,
              P : IsYamlCharWithIndentsReader
{
    private var mode = FlowMapMode.START

    override fun readUntilToken(): JsonToken {
        return if (this.mode == FlowMapMode.START) {
            this.mode = FlowMapMode.KEY
            JsonToken.SimpleStartObject
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
                    } else this.plainStringReader("-")
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
                '|', '>' -> throw InvalidYamlContent("Unsupported character $lastChar in flow map")
                else -> this.plainStringReader("")
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
            JsonToken.Value(value)
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

    private fun plainStringReader(startWith: String): JsonToken {
        return PlainStringReader(
            this.yamlReader,
            this,
            startWith,
            PlainStyleMode.FLOW_MAP
        ) {
            this.constructToken(it)
        }.let {
            this.currentReader = it
            it.readUntilToken()
        }
    }

    override fun childIsDoneReading() {
        this.currentReader = this
    }

    override fun handleReaderInterrupt(): JsonToken {
        this.parentReader.childIsDoneReading()
        return JsonToken.EndObject
    }

    override fun indentCount() = this.parentReader.indentCountForChildren()

    override fun indentCountForChildren() = this.parentReader.indentCountForChildren()

    override fun continueIndentLevel() = this.readUntilToken()

    override fun <P> newIndentLevel(indentCount: Int, parentReader: P): JsonToken
            where P : YamlCharReader,
                  P : IsYamlCharWithChildrenReader,
                  P : IsYamlCharWithIndentsReader {
        return this.readUntilToken()
    }

    override fun endIndentLevel(indentCount: Int, tokenToReturn: (() -> JsonToken)?) =
        this.readUntilToken()

    override fun foundMapKey(isExplicitMap: Boolean) = this.parentReader.foundMapKey(isExplicitMap)
}