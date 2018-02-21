package maryk.core.json.yaml

import maryk.core.json.JsonToken

/** Reader for flow Array Items [item1, item2, item3] */
internal class FlowArrayItemsReader<out P>(
    yamlReader: YamlReaderImpl,
    parentReader: P
) : YamlCharWithParentReader<P>(yamlReader, parentReader),
    IsYamlCharWithChildrenReader
        where P : YamlCharReader,
              P : IsYamlCharWithChildrenReader
{
    private var isStarted = false

    override fun readUntilToken(): JsonToken {
        return if (!this.isStarted) {
            this.isStarted = true
            JsonToken.StartArray
        } else {
            while(this.lastChar.isWhitespace()) {
                read()
            }

            return when(this.lastChar) {
                '\'' -> {
                    read()
                    StringInSingleQuoteReader(this.yamlReader, this) {
                        JsonToken.ArrayValue(it)
                    }.let {
                        this.currentReader = it
                        it.readUntilToken()
                    }
                }
                '\"' -> {
                    read()
                    StringInDoubleQuoteReader(this.yamlReader, this) {
                        JsonToken.ArrayValue(it)
                    }.let {
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
                '-' -> {
                    read()
                    if (this.lastChar.isWhitespace()) {
                        throw InvalidYamlContent("Expected a comma")
                    } else {
                        TODO("simple string reader or fail")
                    }
                }
                ',' -> {
                    read()
                    this.readUntilToken()
                }
                ']' -> {
                    read()
                    this.parentReader.childIsDoneReading()
                    JsonToken.EndArray
                }
                else -> {
                    throw InvalidYamlContent("Unknown character '$lastChar' found")
                }
            }
        }
    }

    override fun childIsDoneReading() {
        this.currentReader = this
    }

    override fun handleReaderInterrupt(): JsonToken {
        this.currentReader = this.parentReader
        return JsonToken.EndArray
    }
}