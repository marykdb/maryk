package maryk.core.json.yaml

import maryk.core.json.ArrayType
import maryk.core.json.JsonToken
import maryk.core.json.TokenType

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
    private var isStarted = false

    override fun readUntilToken(): JsonToken {
        return if (!this.isStarted) {
            this.isStarted = true
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
                '\'' -> {
                    read()
                    StringInSingleQuoteReader(this.yamlReader, this) {
                        createYamlValueToken(it, this.tag, false)
                    }.let {
                        this.currentReader = it
                        it.readUntilToken()
                    }
                }
                '\"' -> {
                    read()
                    StringInDoubleQuoteReader(this.yamlReader, this) {
                        createYamlValueToken(it, this.tag, false)
                    }.let {
                        this.currentReader = it
                        it.readUntilToken()
                    }
                }
                '{' -> {
                    read()
                    FlowMapItemsReader(
                        yamlReader = this.yamlReader,
                        parentReader = this,
                        startTag = this.tag
                    ).let {
                        this.currentReader = it
                        it.readUntilToken()
                    }
                }
                '[' -> {
                    read()
                    FlowSequenceReader(
                        yamlReader = this.yamlReader,
                        parentReader = this,
                        startTag = this.tag
                    ).let {
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
                '-' -> {
                    read()
                    if (this.lastChar.isWhitespace()) {
                        throw InvalidYamlContent("Expected a comma")
                    } else {
                        this.plainStringReader("-")
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
                '|', '>' -> throw InvalidYamlContent("Unsupported character $lastChar in flow array")
                else -> this.plainStringReader("")
            }
        }
    }

    override fun jsonTokenCreator(value: String?, isPlainStringReader: Boolean) =
        createYamlValueToken(value, this.tag, isPlainStringReader)

    override fun handleReaderInterrupt(): JsonToken {
        this.currentReader = this.parentReader
        return JsonToken.EndArray
    }
}