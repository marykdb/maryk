package maryk.core.json.yaml

import maryk.core.extensions.isLineBreak
import maryk.core.json.JsonToken

private enum class ExplicitMapKeyState {
    QUESTION, KEY, VALUE, END, DONE
}

/** Reads Explicit map keys started with ? */
internal class ExplicitMapKeyReader<out P>(
    yamlReader: YamlReaderImpl,
    parentReader: P,
    private val jsonTokenConstructor: (String?) -> JsonToken
) : YamlCharWithParentReader<P>(yamlReader, parentReader),
    IsYamlCharWithIndentsReader,
    IsYamlCharWithChildrenReader
        where P : YamlCharReader,
              P : IsYamlCharWithChildrenReader,
              P : IsYamlCharWithIndentsReader
{
    private var state: ExplicitMapKeyState = ExplicitMapKeyState.QUESTION

    override fun readUntilToken(): JsonToken {
        if(this.state == ExplicitMapKeyState.QUESTION) {
            read()
            // If it turns out to not be an explicit key make it a Plain String reader
            if (!this.lastChar.isWhitespace()) {
                this.parentReader.childIsDoneReading()

                @Suppress("UNCHECKED_CAST")
                return PlainStringReader(
                    this.yamlReader,
                    this.currentReader as P,
                    "?"
                ) {
                    this.jsonTokenConstructor(it)
                }.let {
                    this.currentReader = it
                    it.readUntilToken()
                }
            }

            this.state = ExplicitMapKeyState.KEY

            this.parentReader.foundMapKey(true)?.let {
                return it
            }
        }

        if (this.lastChar.isLineBreak()) {
            return IndentReader(this.yamlReader, this).let {
                this.currentReader = it
                it.readUntilToken()
            }
        }

        TODO("fill")
    }

    override fun indentCount() = this.parentReader.indentCountForChildren()

    override fun indentCountForChildren() = this.parentReader.indentCountForChildren()

    override fun <P> newIndentLevel(parentReader: P): JsonToken
            where P : YamlCharReader,
                  P : IsYamlCharWithChildrenReader,
                  P : IsYamlCharWithIndentsReader {
        TODO("not implemented")
    }

    override fun continueIndentLevel(): JsonToken {
        if (lastChar == ':') {
            read()
            if(this.lastChar.isWhitespace()) {
                this.parentReader.childIsDoneReading()

                return if (this.state == ExplicitMapKeyState.KEY) {
                    this.state = ExplicitMapKeyState.VALUE
                    JsonToken.FieldName(null)
                } else {
                    this.currentReader.readUntilToken()
                }
            } else {
                TODO("back to indent reader")
            }
        } else {
            // Not a map value so assume new value
            this.yamlReader.hasUnclaimedIndenting(this.indentCount())
            this.currentReader = this
            when (this.state) {
                ExplicitMapKeyState.KEY -> {
                    this.state = ExplicitMapKeyState.VALUE
                    return JsonToken.FieldName(null)
                }
                ExplicitMapKeyState.VALUE -> {
                    this.state = ExplicitMapKeyState.END
                    return this.jsonTokenConstructor(null)
                }
                else -> {
                    this.parentReader.childIsDoneReading()
                    return (this.currentReader as IsYamlCharWithIndentsReader).continueIndentLevel()
                }
            }
        }
    }

    override fun endIndentLevel(indentCount: Int, tokenToReturn: JsonToken?): JsonToken {
        TODO("not implemented")
    }

    // Return null because already set explicitly
    override fun foundMapKey(isExplicitMap: Boolean) = null

    override fun childIsDoneReading() {
        this.currentReader = this
    }

    override fun handleReaderInterrupt() = when (this.state) {
        ExplicitMapKeyState.QUESTION -> {
            this.state = ExplicitMapKeyState.KEY
            JsonToken.StartObject
        }
        ExplicitMapKeyState.KEY -> {
            this.state = ExplicitMapKeyState.VALUE
            JsonToken.FieldName(null)
        }
        ExplicitMapKeyState.VALUE -> {
            this.state = ExplicitMapKeyState.END
            JsonToken.ObjectValue(null)
        }
        ExplicitMapKeyState.END -> {
            this.state = ExplicitMapKeyState.DONE
            JsonToken.EndObject
        }
        ExplicitMapKeyState.DONE -> this.parentReader.handleReaderInterrupt()
    }
}