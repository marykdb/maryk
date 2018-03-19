package maryk.core.json.yaml

import maryk.core.extensions.isLineBreak
import maryk.core.json.JsonToken
import maryk.core.json.MapType
import maryk.core.json.TokenType
import maryk.core.json.ValueType

private enum class ExplicitMapKeyState {
    START, KEY, VALUE, END, DONE
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
    private var state: ExplicitMapKeyState = ExplicitMapKeyState.START
    private var tag: TokenType? = null

    override fun readUntilToken(): JsonToken {
        if(this.state == ExplicitMapKeyState.START) {
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
            parentReader = this
        ).let {
            this.currentReader = it
            it.readUntilToken()
        }
    }

    override fun indentCount() = this.parentReader.indentCountForChildren() + 1

    override fun indentCountForChildren() = this.indentCount()

    override fun <P> newIndentLevel(indentCount: Int, parentReader: P, tag: TokenType?): JsonToken
            where P : YamlCharReader,
                  P : IsYamlCharWithChildrenReader,
                  P : IsYamlCharWithIndentsReader =
        this.parentReader.newIndentLevel(indentCount, parentReader, tag)

    override fun continueIndentLevel(tag: TokenType?) = this.readUntilToken()

    override fun endIndentLevel(indentCount: Int, tokenToReturn: (() -> JsonToken)?): JsonToken {
        this.currentReader = this
        if (this.state == ExplicitMapKeyState.KEY) {
            this.state = ExplicitMapKeyState.VALUE
            this.parentReader.childIsDoneReading()
            return tokenToReturn?.let { it() } ?: JsonToken.FieldName(null)
        }
        return this.parentReader.endIndentLevel(indentCount, tokenToReturn)
    }

    // Return null because already set explicitly
    override fun foundMapKey(isExplicitMap: Boolean): JsonToken? = null

    override fun isWithinMap() = this.parentReader.isWithinMap()

    override fun childIsDoneReading() {
        this.currentReader = this
    }

    override fun handleReaderInterrupt() = when (this.state) {
        ExplicitMapKeyState.START -> {
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
