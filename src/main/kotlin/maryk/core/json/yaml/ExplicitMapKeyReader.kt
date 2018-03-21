package maryk.core.json.yaml

import maryk.core.extensions.isLineBreak
import maryk.core.json.JsonToken
import maryk.core.json.MapType
import maryk.core.json.TokenType

private enum class ExplicitMapState {
    STARTED, INTERNAL_MAP, COMPLEX, SIMPLE
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
    private var tag: TokenType? = null

    private var state: ExplicitMapState? = null

    override fun readUntilToken(): JsonToken {
        if (this.state == null) {
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

            this.state = ExplicitMapState.STARTED

            this.parentReader.foundMapKey(true)?.let {
                return it
            }
        }

        val startedOnNewLine = this.lastChar.isLineBreak()
        val currentIndentCount = this.yamlReader.skipEmptyLinesAndCommentsAndCountIndents()

        if (startedOnNewLine && currentIndentCount < this.indentCount()) {
            return this.endIndentLevel(currentIndentCount, null)
        }

        LineReader(
            this.yamlReader,
            parentReader = this,
            indentToAdd = currentIndentCount
        ).let {
            this.currentReader = it
            it.readUntilToken().let {
                if (this.state == ExplicitMapState.STARTED) {
                    if (it !is JsonToken.FieldName) {
                        this.yamlReader.pushToken(it)
                        this.state = ExplicitMapState.COMPLEX
                        return JsonToken.StartComplexFieldName
                    } else {
                        this.state = ExplicitMapState.SIMPLE
                    }
                }
                return it
            }
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
        this.parentReader.childIsDoneReading()

        return when(this.state) {
            ExplicitMapState.INTERNAL_MAP -> {
                this.yamlReader.setUnclaimedIndenting(indentCount)
                this.yamlReader.pushToken(JsonToken.EndComplexFieldName)
                tokenToReturn?.let {
                    this.yamlReader.pushToken(it())
                }
                JsonToken.EndObject
            }
            ExplicitMapState.COMPLEX -> {
                this.yamlReader.setUnclaimedIndenting(indentCount)
                tokenToReturn?.let {
                    return it().also {
                        yamlReader.pushToken(JsonToken.EndComplexFieldName)
                    }
                }
                JsonToken.EndComplexFieldName
            }
            null, ExplicitMapState.STARTED, ExplicitMapState.SIMPLE -> {
                tokenToReturn?.let {
                    return it()
                }

                JsonToken.FieldName(null)
            }
        }
    }

    override fun foundMapKey(isExplicitMap: Boolean): JsonToken? {
        if (this.state != ExplicitMapState.INTERNAL_MAP && this.state != ExplicitMapState.COMPLEX) {
            this.state = ExplicitMapState.INTERNAL_MAP
            this.yamlReader.pushToken(JsonToken.SimpleStartObject)
            return JsonToken.StartComplexFieldName
        }

        return null
    }

    override fun isWithinMap() = this.parentReader.isWithinMap()

    override fun childIsDoneReading() {
        this.currentReader = this
    }

    override fun handleReaderInterrupt() =
        when(this.state) {
            null -> {
                this.state = ExplicitMapState.STARTED
                this.parentReader.foundMapKey(true)?.let {
                    return it
                }

                this.tag?.let {
                    this.tag = null
                    (it as? MapType)?.let {
                        JsonToken.StartObject(it)
                    } ?: throw InvalidYamlContent("Cannot use non map tags on maps")
                } ?: JsonToken.SimpleStartObject
            }
            ExplicitMapState.COMPLEX -> {
                this.parentReader.childIsDoneReading()
                JsonToken.EndComplexFieldName
            }
            ExplicitMapState.INTERNAL_MAP -> {
                this.state = ExplicitMapState.COMPLEX
                JsonToken.EndObject
            }
            ExplicitMapState.STARTED, ExplicitMapState.SIMPLE -> {
                this.parentReader.childIsDoneReading()
                JsonToken.FieldName(null)
            }
        }
}
