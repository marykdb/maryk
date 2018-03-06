package maryk.core.json.yaml

import maryk.core.json.JsonToken

/** Reader for flow sequences [item1, item2, item3] */
internal class FlowSequenceReader<out P>(
    yamlReader: YamlReaderImpl,
    parentReader: P
) : YamlCharWithParentReader<P>(yamlReader, parentReader),
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
            JsonToken.SimpleStartArray
        } else {
            while(this.lastChar.isWhitespace()) {
                read()
            }

            return when(this.lastChar) {
                '\'' -> {
                    read()
                    StringInSingleQuoteReader(this.yamlReader, this) {
                        JsonToken.Value(it)
                    }.let {
                        this.currentReader = it
                        it.readUntilToken()
                    }
                }
                '\"' -> {
                    read()
                    StringInDoubleQuoteReader(this.yamlReader, this) {
                        JsonToken.Value(it)
                    }.let {
                        this.currentReader = it
                        it.readUntilToken()
                    }
                }
                '[' -> {
                    read()
                    FlowSequenceReader(
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

    private fun plainStringReader(startWith: String): JsonToken {
        return PlainStringReader(
            this.yamlReader,
            this,
            startWith,
            PlainStyleMode.FLOW_COLLECTION
        ) {
            JsonToken.Value(it)
        }.let {
            this.currentReader = it
            it.readUntilToken()
        }
    }

    override fun childIsDoneReading() {
        this.currentReader = this
    }

    override fun handleReaderInterrupt(): JsonToken {
        this.currentReader = this.parentReader
        return JsonToken.EndArray
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

    override fun foundMapKey(isExplicitMap: Boolean) =
        this.parentReader.foundMapKey(isExplicitMap)
}