package maryk.core.json.yaml

import maryk.core.extensions.isLineBreak
import maryk.core.json.JsonToken
import maryk.core.json.MapType
import maryk.core.json.TokenType
import maryk.core.json.ValueType

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
    private var tag: TokenType? = null

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

        return LineReader(
            this.yamlReader,
            parentReader = this,
            indentToAdd = 1
        ).let {
            this.currentReader = it
            it.readUntilToken()
        }
    }

    override fun indentCount() = this.parentReader.indentCountForChildren()

    override fun indentCountForChildren() = this.parentReader.indentCountForChildren()

    override fun <P> newIndentLevel(indentCount: Int, parentReader: P, tag: TokenType?): JsonToken
            where P : YamlCharReader,
                  P : IsYamlCharWithChildrenReader,
                  P : IsYamlCharWithIndentsReader {
        TODO("not implemented")
    }

    override fun continueIndentLevel(tag: TokenType?): JsonToken {
        this.tag = tag
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
            this.currentReader = this
            // Not a map value so assume new value
            return closeCurrentReader()
        }
    }

    override fun endIndentLevel(indentCount: Int, tokenToReturn: (() -> JsonToken)?): JsonToken {
        return if (this.indentCount() == indentCount) {
            if (tokenToReturn != null) {
                updateState()
                tokenToReturn()
            } else {
                this.continueIndentLevel(null)
            }
        } else {
            this.parentReader.endIndentLevel(indentCount, tokenToReturn)
        }
    }

    private fun closeCurrentReader(): JsonToken {
        this.yamlReader.hasUnclaimedIndenting(this.indentCount())
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
                return (this.currentReader as IsYamlCharWithIndentsReader).continueIndentLevel(null)
            }
        }
    }

    // Return null because already set explicitly
    override fun foundMapKey(isExplicitMap: Boolean): JsonToken? = null

    override fun isWithinMap() = this.parentReader.isWithinMap()

    override fun childIsDoneReading() {
        updateState()
        this.currentReader = this
    }

    private fun updateState() {
        if (this.state == ExplicitMapKeyState.KEY) {
            this.state = ExplicitMapKeyState.VALUE
        } else if (this.state == ExplicitMapKeyState.VALUE) {
            this.state = ExplicitMapKeyState.DONE
        }
    }

    override fun handleReaderInterrupt() = when (this.state) {
        ExplicitMapKeyState.QUESTION -> {
            this.state = ExplicitMapKeyState.KEY
            this.tag?.let {
                this.tag = null
                (it as? MapType)?.let {
                    JsonToken.StartObject(it)
                } ?: throw InvalidYamlContent("Cannot use non map tags on maps")
            } ?: JsonToken.SimpleStartObject
        }
        ExplicitMapKeyState.KEY -> {
            this.state = ExplicitMapKeyState.VALUE
            JsonToken.FieldName(null)
        }
        ExplicitMapKeyState.VALUE -> {
            this.state = ExplicitMapKeyState.END
            JsonToken.Value(null, ValueType.String)
        }
        ExplicitMapKeyState.END -> {
            this.state = ExplicitMapKeyState.DONE
            JsonToken.EndObject
        }
        ExplicitMapKeyState.DONE -> this.parentReader.handleReaderInterrupt()
    }
}