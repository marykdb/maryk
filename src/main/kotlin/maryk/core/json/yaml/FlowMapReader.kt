package maryk.core.json.yaml

import maryk.core.json.JsonToken
import maryk.core.json.MapType
import maryk.core.json.TokenType

private enum class FlowMapMode {
    START, KEY, VALUE, SEPARATOR, STOP
}

/** Reader for flow Map Items {key1: value1, key2: value2} */
internal class FlowMapItemsReader<out P>(
    yamlReader: YamlReaderImpl,
    parentReader: P,
    startTag: TokenType?
) : YamlCharWithParentReader<P>(yamlReader, parentReader),
    IsYamlCharWithChildrenReader, IsYamlCharWithIndentsReader
        where P : YamlCharReader,
              P : IsYamlCharWithChildrenReader,
              P : IsYamlCharWithIndentsReader
{
    private var tag: TokenType? = startTag

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
                    StringInSingleQuoteReader(this.yamlReader, this, { this.constructToken(it, false) }).let {
                        this.currentReader = it
                        it.readUntilToken()
                    }
                }
                '\"' -> {
                    read()
                    StringInDoubleQuoteReader(this.yamlReader, this, { this.constructToken(it, false) }).let {
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
                        return this.constructToken(null, false)
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

    private fun constructToken(value: String?, isPlainStringReader: Boolean) = when(mode) {
        FlowMapMode.START -> throw InvalidYamlContent("Map cannot be in start mode")
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

    private fun plainStringReader(startWith: String): JsonToken {
        return PlainStringReader(
            this.yamlReader,
            this,
            startWith,
            PlainStyleMode.FLOW_MAP
        ) {
            this.constructToken(it, true)
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

    override fun continueIndentLevel(tag: TokenType?): JsonToken {
        this.tag = tag
        return this.readUntilToken()
    }

    override fun <P> newIndentLevel(indentCount: Int, parentReader: P, tag: TokenType?): JsonToken
            where P : YamlCharReader,
                  P : IsYamlCharWithChildrenReader,
                  P : IsYamlCharWithIndentsReader {
        this.tag = tag
        return this.readUntilToken()
    }

    override fun endIndentLevel(indentCount: Int, tokenToReturn: (() -> JsonToken)?) =
        this.readUntilToken()

    override fun foundMapKey(isExplicitMap: Boolean) = this.parentReader.foundMapKey(isExplicitMap)

    override fun isWithinMap() = this.parentReader.isWithinMap()
}